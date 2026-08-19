package com.halanoi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class WebsiteBrand(
    val displayName: String,
    val iconEmoji: String,
    val brandColor: Color,
    val category: String
)

data class KeywordTag(
    val keyword: String,
    val iconEmoji: String,
    val tagColor: Color,
    val category: String
)

object BrandIconHelper {

    fun getWebsiteBrand(domain: String): WebsiteBrand {
        val clean = domain.lowercase().trim()
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")
            .split("/").first()

        return when {
            clean.contains("reddit") -> WebsiteBrand("Reddit", "🔴", Color(0xFFFF4500), "Forum & Memes")
            clean.contains("twitter") || clean == "x.com" -> WebsiteBrand("X / Twitter", "𝕏", Color(0xFF1D9BF0), "Social Media")
            clean.contains("instagram") -> WebsiteBrand("Instagram", "📸", Color(0xFFE1306C), "Photo & Reels")
            clean.contains("facebook") || clean.contains("meta.com") -> WebsiteBrand("Facebook", "📘", Color(0xFF1877F2), "Social Network")
            clean.contains("tiktok") -> WebsiteBrand("TikTok", "🎵", Color(0xFFEE1D52), "Short Video")
            clean.contains("youtube") || clean.contains("youtu.be") -> WebsiteBrand("YouTube", "▶️", Color(0xFFFF0000), "Video Streaming")
            clean.contains("netflix") -> WebsiteBrand("Netflix", "🎬", Color(0xFFE50914), "Movies & TV")
            clean.contains("twitch") -> WebsiteBrand("Twitch", "👾", Color(0xFF9146FF), "Live Streaming")
            clean.contains("discord") -> WebsiteBrand("Discord", "💬", Color(0xFF5865F2), "Community Chat")
            clean.contains("disney") -> WebsiteBrand("Disney+", "🏰", Color(0xFF113CCF), "Entertainment")
            clean.contains("primevideo") || clean.contains("amazon") -> WebsiteBrand("Prime Video", "📦", Color(0xFF00A8E1), "Streaming")
            clean.contains("hulu") -> WebsiteBrand("Hulu", "🟢", Color(0xFF1CE783), "Streaming")
            clean.contains("pinterest") || clean.contains("pinimg") -> WebsiteBrand("Pinterest", "📌", Color(0xFFE60023), "Visual Discovery")
            clean.contains("tumblr") -> WebsiteBrand("Tumblr", "🔷", Color(0xFF36465D), "Blogging")
            clean.contains("deviantart") -> WebsiteBrand("DeviantArt", "🎨", Color(0xFF05CC47), "Art Community")
            clean.contains("spotify") -> WebsiteBrand("Spotify", "🎧", Color(0xFF1DB954), "Music & Podcasts")
            clean.contains("snapchat") -> WebsiteBrand("Snapchat", "👻", Color(0xFFFFFC00), "Ephemeral Chat")
            clean.contains("steam") || clean.contains("roblox") || clean.contains("epicgames") -> WebsiteBrand("Gaming Platform", "🎮", Color(0xFF66C0F4), "Games")
            clean.contains("pornhub") || clean.contains("xvideos") || clean.contains("xnxx") -> WebsiteBrand("Adult Portal", "🔞", Color(0xFFFF9900), "Adult Content")
            clean.contains("imgur") || clean.contains("flickr") || clean.contains("vsco") -> WebsiteBrand(clean.capitalizeFirst(), "🖼️", Color(0xFF2BA968), "Image Hosting")
            else -> WebsiteBrand(clean.capitalizeFirst(), "🌐", Color(0xFF38BDF8), "Web Domain")
        }
    }

    fun getKeywordTag(keyword: String): KeywordTag {
        val clean = keyword.lowercase().trim()
        return when {
            clean.contains("game") || clean.contains("gaming") || clean.contains("fortnite") || 
            clean.contains("minecraft") || clean.contains("valorant") || clean.contains("bgmi") || 
            clean.contains("pubg") || clean.contains("gta") || clean.contains("steam") -> 
                KeywordTag(clean, "🎮", Color(0xFF8B5CF6), "Gaming")

            clean.contains("reel") || clean.contains("short") || clean.contains("tiktok") || 
            clean.contains("scroll") || clean.contains("feed") -> 
                KeywordTag(clean, "📱", Color(0xFFEC4899), "Short Video")

            clean.contains("porn") || clean.contains("nsfw") || clean.contains("adult") || 
            clean.contains("sex") || clean.contains("erotic") || clean.contains("xxx") -> 
                KeywordTag(clean, "🔞", Color(0xFFEF4444), "Adult / NSFW")

            clean.contains("sport") || clean.contains("cricket") || clean.contains("football") || 
            clean.contains("soccer") || clean.contains("nba") || clean.contains("ipl") -> 
                KeywordTag(clean, "⚽", Color(0xFF10B981), "Sports")

            clean.contains("movie") || clean.contains("cinema") || clean.contains("anime") || 
            clean.contains("drama") || clean.contains("series") -> 
                KeywordTag(clean, "🎬", Color(0xFFF59E0B), "Entertainment")

            clean.contains("politic") || clean.contains("news") || clean.contains("debate") || 
            clean.contains("election") || clean.contains("trump") || clean.contains("modi") -> 
                KeywordTag(clean, "📰", Color(0xFF06B6D4), "News & Politics")

            clean.contains("crypto") || clean.contains("bitcoin") || clean.contains("trading") || 
            clean.contains("stock") || clean.contains("forex") -> 
                KeywordTag(clean, "📈", Color(0xFF3B82F6), "Crypto & Trading")

            clean.contains("casino") || clean.contains("gambling") || clean.contains("betting") || 
            clean.contains("stake") || clean.contains("poker") -> 
                KeywordTag(clean, "🎰", Color(0xFFD946EF), "Gambling")

            clean.contains("shop") || clean.contains("amazon") || clean.contains("flipkart") || 
            clean.contains("deal") || clean.contains("sale") -> 
                KeywordTag(clean, "🛍️", Color(0xFFF97316), "Shopping")

            clean.contains("date") || clean.contains("dating") || clean.contains("tinder") || 
            clean.contains("bumble") || clean.contains("chat") -> 
                KeywordTag(clean, "💬", Color(0xFFE11D48), "Dating & Social")

            else -> KeywordTag(clean, "🎯", Color(0xFF6366F1), "Keyword")
        }
    }

    private fun String.capitalizeFirst(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Composable
fun WebsiteFavicon(
    domain: String,
    emoji: String,
    brandColor: Color,
    modifier: Modifier = Modifier.size(36.dp)
) {
    val clean = domain.lowercase().trim()
        .replace("https://", "")
        .replace("http://", "")
        .replace("www.", "")
        .split("/").first()

    val domainToFetch = if (!clean.contains(".")) "$clean.com" else clean
    var isError by remember(domain) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(brandColor.copy(alpha = 0.12f))
            .border(1.dp, brandColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!isError) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://www.google.com/s2/favicons?sz=128&domain=$domainToFetch")
                    .crossfade(true)
                    .build(),
                contentDescription = "$domain Favicon",
                modifier = Modifier.size(22.dp),
                onError = { isError = true }
            )
        } else {
            Text(text = emoji, fontSize = 16.sp)
        }
    }
}
