import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_rounded_mask(size, radius):
    # Supersampled mask for anti-aliasing
    scale = 4
    large_size = (size[0] * scale, size[1] * scale)
    mask = Image.new('L', large_size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), large_size], radius=radius * scale, fill=255)
    return mask.resize(size, Image.Resampling.LANCZOS)

def make_phone_mockup(screenshot_path, target_height=780, dark_theme=True):
    orig = Image.open(screenshot_path).convert('RGBA')
    w, h = orig.size
    aspect = w / h
    
    screen_h = target_height
    screen_w = int(screen_h * aspect)
    screen_resized = orig.resize((screen_w, screen_h), Image.Resampling.LANCZOS)
    
    bezel = 14
    phone_w = screen_w + (bezel * 2)
    phone_h = screen_h + (bezel * 2)
    corner_radius = 42
    screen_radius = 32
    
    phone = Image.new('RGBA', (phone_w, phone_h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(phone)
    
    if dark_theme:
        body_color = (20, 26, 38, 255)
        rim_color = (70, 85, 115, 255)
    else:
        body_color = (255, 255, 255, 255)
        rim_color = (210, 218, 230, 255)
        
    draw.rounded_rectangle([(0, 0), (phone_w - 1, phone_h - 1)], radius=corner_radius, fill=body_color, outline=rim_color, width=2)
    
    # Clip screen
    screen_mask = create_rounded_mask((screen_w, screen_h), radius=screen_radius)
    phone.paste(screen_resized, (bezel, bezel), screen_mask)
    
    # Camera punch hole
    camera_radius = 6
    cam_x = phone_w // 2
    cam_y = bezel + 12
    draw.ellipse([(cam_x - camera_radius, cam_y - camera_radius), (cam_x + camera_radius, cam_y + camera_radius)], fill=(8, 10, 14, 255))
    
    # Multi-layer deep drop shadow
    pad = 80
    shadow_canvas = Image.new('RGBA', (phone_w + pad * 2, phone_h + pad * 2), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow_canvas)
    
    s_color1 = (0, 0, 0, 140) if dark_theme else (20, 30, 50, 70)
    s_draw.rounded_rectangle([(pad, pad + 25), (pad + phone_w, pad + phone_h + 25)], radius=corner_radius, fill=s_color1)
    shadow_canvas = shadow_canvas.filter(ImageFilter.GaussianBlur(radius=28))
    
    # Paste phone onto shadow
    shadow_canvas.paste(phone, (pad, pad), phone)
    return shadow_canvas

def draw_pill_badge(draw, x, y, dot_color, text, font, font_tag, dark_theme=True):
    bbox = font.getbbox(text)
    text_w = bbox[2] - bbox[0]
    h = 48
    dot_radius = 5
    pad_left = 22
    dot_x = x + pad_left + dot_radius
    dot_y = y + (h // 2)
    text_x = dot_x + dot_radius + 14
    w = text_x + text_w + 24 - x
    
    if dark_theme:
        bg_fill = (18, 25, 38, 255)
        border = (45, 60, 85, 200)
        text_color = (226, 232, 240, 255)
    else:
        bg_fill = (255, 255, 255, 255)
        border = (210, 220, 235, 255)
        text_color = (30, 41, 59, 255)
        
    draw.rounded_rectangle([(x, y), (x + w, y + h)], radius=h // 2, fill=bg_fill, outline=border, width=1)
    # Glowing dot
    draw.ellipse([(dot_x - dot_radius, dot_y - dot_radius), (dot_x + dot_radius, dot_y + dot_radius)], fill=dot_color)
    draw.text((text_x, y + 9), text, fill=text_color, font=font)
    return w

def build_dark_banner():
    canvas_w, canvas_h = 2400, 1200
    dark_bg = (10, 14, 23)
    
    banner = Image.new('RGBA', (canvas_w, canvas_h), (*dark_bg, 255))
    
    # Ambient glows behind phones
    glow = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    g_draw = ImageDraw.Draw(glow)
    g_draw.ellipse([(1100, 150), (2200, 1050)], fill=(16, 185, 129, 32))  # Emerald glow
    g_draw.ellipse([(1300, 50), (2350, 850)], fill=(14, 165, 233, 28))    # Sky blue glow
    glow = glow.filter(ImageFilter.GaussianBlur(radius=180))
    banner = Image.alpha_composite(banner, glow)
    
    draw = ImageDraw.Draw(banner)
    
    # Fonts
    font_bold = "C:/Windows/Fonts/segoeuib.ttf"
    font_reg = "C:/Windows/Fonts/segoeui.ttf"
    
    font_title = ImageFont.truetype(font_bold, 86)
    font_sub_bold = ImageFont.truetype(font_bold, 44)
    font_desc = ImageFont.truetype(font_reg, 30)
    font_badge = ImageFont.truetype(font_bold, 24)
    font_tag = ImageFont.truetype(font_bold, 20)
    
    left_x = 130
    curr_y = 150
    
    # App Icon & Brand tag
    icon_path = 'd:/Halanoi_Fresh/HalanoiApp/graphics/icon.png'
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert('RGBA')
        icon = icon.resize((92, 92), Image.Resampling.LANCZOS)
        banner.paste(icon, (left_x, curr_y), icon)
        
        pill_x = left_x + 112
        pill_y = curr_y + 24
        draw.rounded_rectangle([(pill_x, pill_y), (pill_x + 300, pill_y + 44)], radius=22, fill=(20, 32, 48, 255), outline=(56, 189, 248, 140), width=1)
        draw.text((pill_x + 22, pill_y + 10), "OPEN-SOURCE SECURITY", fill=(56, 189, 248, 255), font=font_tag)
        
    curr_y += 130
    
    # Main Title
    draw.text((left_x, curr_y), "Halanoi Sovereign", fill=(255, 255, 255, 255), font=font_title)
    curr_y += 115
    
    # Taglines (StoryPad style contrast)
    draw.text((left_x, curr_y), "Not just another focus timer.", fill=(203, 213, 225, 255), font=font_sub_bold)
    curr_y += 58
    draw.text((left_x, curr_y), "Sovereign lockdown for your digital life.", fill=(52, 211, 153, 255), font=font_sub_bold)
    curr_y += 84
    
    # Paragraph Description
    desc_lines = [
        "A high-security, bypass-proof focus lockdown system for Android.",
        "Combines OS-Level Device Owner policies with an offline Edge AI",
        "vision sniper and DNS interception to eliminate digital distractions."
    ]
    for line in desc_lines:
        draw.text((left_x, curr_y), line, fill=(148, 163, 184, 255), font=font_desc)
        curr_y += 44
        
    curr_y += 50
    
    # Badges
    badges_row1 = [
        ((16, 185, 129), "OS-Level Device Owner"),
        ((168, 85, 247), "Local Edge AI Classifiers"),
    ]
    badges_row2 = [
        ((6, 182, 212), "Cloudflare Family DNS"),
        ((249, 115, 22), "Anti-Bypass App Vault"),
    ]
    badges_row3 = [
        ((59, 130, 246), "Android 10+ (API 29-36)"),
        ((148, 163, 184), "GPL-3.0 Open Source"),
    ]
    
    for row in [badges_row1, badges_row2, badges_row3]:
        rx = left_x
        for dot_color, text in row:
            w = draw_pill_badge(draw, rx, curr_y, dot_color, text, font_badge, font_tag, dark_theme=True)
            rx += w + 16
        curr_y += 62
        
    # RIGHT SIDE: Staggered phone mockups with REAL screenshots
    attach_dir = 'd:/Halanoi_Fresh/HalanoiApp/attachments'
    
    # 1. Back Left: Vault - System Blocked Apps (1000008437.jpg)
    phone_vault = make_phone_mockup(os.path.join(attach_dir, '1000008437.jpg'), target_height=750, dark_theme=True)
    
    # 2. Back Right: AI Vision Classifiers (1000008439.jpg)
    phone_ai = make_phone_mockup(os.path.join(attach_dir, '1000008439.jpg'), target_height=750, dark_theme=True)
    
    # 3. Center Foreground: Protection Active Radar (1000008436.jpg)
    phone_main = make_phone_mockup(os.path.join(attach_dir, '1000008436.jpg'), target_height=860, dark_theme=True)
    
    # Layer placement (X, Y)
    # Note: phone mockup has pad=80 around it for shadow
    banner.paste(phone_vault, (1160, 100), phone_vault)
    banner.paste(phone_ai, (1780, 100), phone_ai)
    banner.paste(phone_main, (1430, 160), phone_main)
    
    out_jpg = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner.jpg'
    out_png = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner.png'
    
    banner_rgb = banner.convert('RGB')
    banner_rgb.save(out_jpg, quality=95, optimize=True)
    banner.save(out_png)
    print("Dark banner generated successfully!")

def build_light_banner():
    canvas_w, canvas_h = 2400, 1200
    # Clean warm studio light background (just like StoryPad)
    light_bg = (248, 249, 251)
    
    banner = Image.new('RGBA', (canvas_w, canvas_h), (*light_bg, 255))
    
    # Soft warm lighting
    glow = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    g_draw = ImageDraw.Draw(glow)
    g_draw.ellipse([(1100, 100), (2300, 1100)], fill=(16, 185, 129, 20))
    g_draw.ellipse([(1300, 200), (2200, 900)], fill=(59, 130, 246, 18))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=180))
    banner = Image.alpha_composite(banner, glow)
    
    draw = ImageDraw.Draw(banner)
    
    font_bold = "C:/Windows/Fonts/segoeuib.ttf"
    font_reg = "C:/Windows/Fonts/segoeui.ttf"
    
    font_title = ImageFont.truetype(font_bold, 86)
    font_sub_bold = ImageFont.truetype(font_bold, 44)
    font_desc = ImageFont.truetype(font_reg, 30)
    font_badge = ImageFont.truetype(font_bold, 24)
    font_tag = ImageFont.truetype(font_bold, 20)
    
    left_x = 130
    curr_y = 150
    
    icon_path = 'd:/Halanoi_Fresh/HalanoiApp/graphics/icon.png'
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert('RGBA')
        icon = icon.resize((92, 92), Image.Resampling.LANCZOS)
        banner.paste(icon, (left_x, curr_y), icon)
        
        pill_x = left_x + 112
        pill_y = curr_y + 24
        draw.rounded_rectangle([(pill_x, pill_y), (pill_x + 300, pill_y + 44)], radius=22, fill=(238, 242, 255, 255), outline=(99, 102, 241, 140), width=1)
        draw.text((pill_x + 22, pill_y + 10), "OPEN-SOURCE SECURITY", fill=(79, 70, 229, 255), font=font_tag)
        
    curr_y += 130
    
    draw.text((left_x, curr_y), "Halanoi Sovereign", fill=(15, 23, 42, 255), font=font_title)
    curr_y += 115
    
    draw.text((left_x, curr_y), "Not just another focus timer.", fill=(71, 85, 105, 255), font=font_sub_bold)
    curr_y += 58
    # Emerald accent
    draw.text((left_x, curr_y), "Sovereign lockdown for your digital life.", fill=(5, 150, 105, 255), font=font_sub_bold)
    curr_y += 84
    
    desc_lines = [
        "A high-security, bypass-proof focus lockdown system for Android.",
        "Combines OS-Level Device Owner policies with an offline Edge AI",
        "vision sniper and DNS interception to eliminate digital distractions."
    ]
    for line in desc_lines:
        draw.text((left_x, curr_y), line, fill=(100, 116, 139, 255), font=font_desc)
        curr_y += 44
        
    curr_y += 50
    
    badges_row1 = [
        ((16, 185, 129), "OS-Level Device Owner"),
        ((147, 51, 234), "Local Edge AI Classifiers"),
    ]
    badges_row2 = [
        ((8, 145, 178), "Cloudflare Family DNS"),
        ((234, 88, 12), "Anti-Bypass App Vault"),
    ]
    badges_row3 = [
        ((37, 99, 235), "Android 10+ (API 29-36)"),
        ((100, 116, 139), "GPL-3.0 Open Source"),
    ]
    
    for row in [badges_row1, badges_row2, badges_row3]:
        rx = left_x
        for dot_color, text in row:
            w = draw_pill_badge(draw, rx, curr_y, dot_color, text, font_badge, font_tag, dark_theme=False)
            rx += w + 16
        curr_y += 62
        
    # RIGHT SIDE: Real Light Mode screenshots (1000008444, 1000008445, 1000008446)
    attach_dir = 'd:/Halanoi_Fresh/HalanoiApp/attachments'
    
    # 1. Back Left: Light Vault (1000008445.jpg)
    phone_vault = make_phone_mockup(os.path.join(attach_dir, '1000008445.jpg'), target_height=750, dark_theme=False)
    
    # 2. Back Right: Light Content Protection Hub (1000008446.jpg)
    phone_filters = make_phone_mockup(os.path.join(attach_dir, '1000008446.jpg'), target_height=750, dark_theme=False)
    
    # 3. Center: Light Main Shield Dashboard (1000008444.jpg)
    phone_main = make_phone_mockup(os.path.join(attach_dir, '1000008444.jpg'), target_height=860, dark_theme=False)
    
    banner.paste(phone_vault, (1160, 100), phone_vault)
    banner.paste(phone_filters, (1780, 100), phone_filters)
    banner.paste(phone_main, (1430, 160), phone_main)
    
    out_jpg = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner_light.jpg'
    out_png = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner_light.png'
    
    banner_rgb = banner.convert('RGB')
    banner_rgb.save(out_jpg, quality=95, optimize=True)
    banner.save(out_png)
    print("Light banner generated successfully!")

if __name__ == '__main__':
    build_dark_banner()
    build_light_banner()
