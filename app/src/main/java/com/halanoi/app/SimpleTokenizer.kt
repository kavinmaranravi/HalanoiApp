package com.halanoi.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class SimpleTokenizer(context: Context) {
    private val vocab = mutableMapOf<String, Int>()
    
    // DistilBERT special tokens
    private val PAD_ID = 0
    private val UNK_ID = 100
    private val CLS_ID = 101
    private val SEP_ID = 102
    val MAX_LEN = 128

    init {
        // Load your vocab.txt from assets
        val inputStream = context.assets.open("vocab.txt")
        val reader = BufferedReader(InputStreamReader(inputStream))
        var id = 0
        reader.forEachLine { line ->
            vocab[line] = id
            id++
        }
        reader.close()
    }

    fun tokenize(text: String): Pair<IntArray, IntArray> {
        val inputIds = IntArray(MAX_LEN) { PAD_ID }
        val attentionMask = IntArray(MAX_LEN) { PAD_ID }

        val tokenIds = mutableListOf<Int>()
        tokenIds.add(CLS_ID)

        // 1. Lowercase and split by spaces OR punctuation (keeping punctuation as separate tokens)
        val cleanText = text.lowercase().trim()
        val words = cleanText.split(Regex("(?<=\\p{Punct})|(?=\\p{Punct})|\\s+")).filter { it.isNotBlank() }

        // 2. WordPiece Tokenization Algorithm
        for (word in words) {
            var start = 0
            var isBad = false
            val subTokens = mutableListOf<Int>()

            while (start < word.length) {
                var end = word.length
                var curSubStr = ""
                var matchId = -1

                // Longest substring match
                while (start < end) {
                    val subStr = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
                    if (vocab.containsKey(subStr)) {
                        curSubStr = subStr
                        matchId = vocab[subStr]!!
                        break
                    }
                    end--
                }

                if (curSubStr.isEmpty()) {
                    isBad = true
                    break
                }
                subTokens.add(matchId)
                start = end
            }

            if (isBad) {
                tokenIds.add(UNK_ID)
            } else {
                tokenIds.addAll(subTokens)
            }
        }

        tokenIds.add(SEP_ID)

        // 3. Fill the arrays up to MAX_LEN (128)
        for (i in 0 until minOf(tokenIds.size, MAX_LEN)) {
            inputIds[i] = tokenIds[i]
            attentionMask[i] = 1 // 1 means real token, 0 means padding
        }

        return Pair(inputIds, attentionMask)
    }
}