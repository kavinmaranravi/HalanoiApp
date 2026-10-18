import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_rounded_mask(size, radius):
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
    
    # Multi-layer soft drop shadow
    pad = 80
    shadow_canvas = Image.new('RGBA', (phone_w + pad * 2, phone_h + pad * 2), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow_canvas)
    
    s_color = (0, 0, 0, 140) if dark_theme else (20, 30, 50, 60)
    s_draw.rounded_rectangle([(pad, pad + 25), (pad + phone_w, pad + phone_h + 25)], radius=corner_radius, fill=s_color)
    shadow_canvas = shadow_canvas.filter(ImageFilter.GaussianBlur(radius=28))
    
    shadow_canvas.paste(phone, (pad, pad), phone)
    return shadow_canvas

def draw_vector_icon(draw, icon_type, cx, cy, color):
    if icon_type == "shield":
        s = 13
        points = [
            (cx - s, cy - s + 2),
            (cx + s, cy - s + 2),
            (cx + s, cy + 2),
            (cx, cy + s + 4),
            (cx - s, cy + 2)
        ]
        draw.polygon(points, fill=None, outline=color, width=2)
        draw.line([(cx - 5, cy + 2), (cx - 1, cy + 6), (cx + 6, cy - 2)], fill=color, width=2)
    elif icon_type == "brain":
        s = 12
        draw.ellipse([(cx - s, cy - s + 2), (cx - 1, cy + s - 2)], outline=color, width=2)
        draw.ellipse([(cx + 1, cy - s + 2), (cx + s, cy + s - 2)], outline=color, width=2)
        draw.ellipse([(cx - s + 3, cy - 2), (cx - 3, cy + 3)], fill=color)
        draw.ellipse([(cx + 3, cy - 2), (cx + s - 3, cy + 3)], fill=color)
    elif icon_type == "globe":
        s = 13
        draw.ellipse([(cx - s, cy - s), (cx + s, cy + s)], outline=color, width=2)
        draw.line([(cx - s, cy), (cx + s, cy)], fill=color, width=2)
        draw.ellipse([(cx - s//2, cy - s), (cx + s//2, cy + s)], outline=color, width=2)
    elif icon_type == "lock":
        s = 11
        draw.arc([(cx - s + 3, cy - s - 4), (cx + s - 3, cy + 2)], start=180, end=0, fill=color, width=3)
        draw.rounded_rectangle([(cx - s, cy - 1), (cx + s, cy + s + 1)], radius=3, fill=color)
        draw.ellipse([(cx - 2, cy + 3), (cx + 2, cy + 7)], fill=(10, 14, 23, 255))
    elif icon_type == "android":
        s = 13
        draw.chord([(cx - s, cy - 6), (cx + s, cy + 8)], start=180, end=0, fill=color)
        draw.line([(cx - 6, cy - 5), (cx - 10, cy - 11)], fill=color, width=2)
        draw.line([(cx + 6, cy - 5), (cx + 10, cy - 11)], fill=color, width=2)
        draw.ellipse([(cx - 5, cy - 1), (cx - 3, cy + 1)], fill=(10, 14, 23, 255))
        draw.ellipse([(cx + 3, cy - 1), (cx + 5, cy + 1)], fill=(10, 14, 23, 255))
    elif icon_type == "code":
        draw.line([(cx - 6, cy - 6), (cx - 11, cy)], fill=color, width=2)
        draw.line([(cx - 11, cy), (cx - 6, cy + 6)], fill=color, width=2)
        draw.line([(cx + 2, cy - 8), (cx - 2, cy + 8)], fill=color, width=2)
        draw.line([(cx + 6, cy - 6), (cx + 11, cy)], fill=color, width=2)
        draw.line([(cx + 11, cy), (cx + 6, cy + 6)], fill=color, width=2)

def draw_rich_badge(draw, x, y, w, h, icon_type, icon_bg, title, subtitle, font_title, font_sub, dark_theme=True):
    if dark_theme:
        card_bg = (16, 23, 35, 240)
        card_border = (38, 52, 75, 180)
        title_color = (255, 255, 255, 255)
        sub_color = (148, 163, 184, 255)
        icon_fg = (255, 255, 255, 255)
    else:
        card_bg = (255, 255, 255, 240)
        card_border = (220, 230, 242, 220)
        title_color = (15, 23, 42, 255)
        sub_color = (100, 116, 139, 255)
        icon_fg = (255, 255, 255, 255)

    # Card background
    draw.rounded_rectangle([(x, y), (x + w, y + h)], radius=h // 2, fill=card_bg, outline=card_border, width=1)
    
    # Circular icon badge
    circle_d = 50
    circle_x = x + 12
    circle_y = y + (h - circle_d) // 2
    draw.ellipse([(circle_x, circle_y), (circle_x + circle_d, circle_y + circle_d)], fill=icon_bg)
    
    # Vector icon
    cx = circle_x + circle_d // 2
    cy = circle_y + circle_d // 2
    draw_vector_icon(draw, icon_type, cx, cy, icon_fg)
    
    # Text
    text_x = circle_x + circle_d + 16
    draw.text((text_x, y + 13), title, fill=title_color, font=font_title)
    draw.text((text_x, y + 41), subtitle, fill=sub_color, font=font_sub)

def build_dark_banner():
    canvas_w, canvas_h = 2400, 1200
    dark_bg = (10, 14, 23)
    
    banner = Image.new('RGBA', (canvas_w, canvas_h), (*dark_bg, 255))
    
    # Ambient glows
    glow = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    g_draw = ImageDraw.Draw(glow)
    g_draw.ellipse([(1100, 150), (2200, 1050)], fill=(16, 185, 129, 36))
    g_draw.ellipse([(1300, 50), (2350, 850)], fill=(14, 165, 233, 30))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=180))
    banner = Image.alpha_composite(banner, glow)
    
    draw = ImageDraw.Draw(banner)
    
    font_bold = "C:/Windows/Fonts/segoeuib.ttf"
    font_reg = "C:/Windows/Fonts/segoeui.ttf"
    
    font_main_title = ImageFont.truetype(font_bold, 86)
    font_sub_bold = ImageFont.truetype(font_bold, 44)
    font_desc = ImageFont.truetype(font_reg, 30)
    font_badge_title = ImageFont.truetype(font_bold, 22)
    font_badge_sub = ImageFont.truetype(font_reg, 17)
    
    left_x = 130
    curr_y = 125
    
    # App Logo (ONLY the icon, NO "OPEN-SOURCE SECURITY" pill!)
    icon_path = 'd:/Halanoi_Fresh/HalanoiApp/graphics/icon.png'
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert('RGBA')
        icon = icon.resize((96, 96), Image.Resampling.LANCZOS)
        banner.paste(icon, (left_x, curr_y), icon)
        
    curr_y += 120
    
    # Title & Taglines
    draw.text((left_x, curr_y), "Halanoi Sovereign", fill=(255, 255, 255, 255), font=font_main_title)
    curr_y += 112
    
    draw.text((left_x, curr_y), "Not just another focus timer.", fill=(203, 213, 225, 255), font=font_sub_bold)
    curr_y += 56
    draw.text((left_x, curr_y), "Sovereign lockdown for your digital life.", fill=(52, 211, 153, 255), font=font_sub_bold)
    curr_y += 80
    
    desc_lines = [
        "A high-security, bypass-proof focus lockdown system for Android.",
        "Combines OS-Level Device Owner policies with an offline Edge AI",
        "vision sniper and DNS interception to eliminate digital distractions."
    ]
    for line in desc_lines:
        draw.text((left_x, curr_y), line, fill=(148, 163, 184, 255), font=font_desc)
        curr_y += 42
        
    curr_y += 46
    
    # 6 Rich Badges in 2 columns
    badges_col1 = [
        ("shield", (16, 185, 129), "OS-Level Device Owner", "Unbypassable lockdown"),
        ("globe", (14, 165, 233), "Cloudflare Family DNS", "Blocks harmful sites"),
        ("android", (34, 197, 94), "Android 10+ (API 29-36)", "Wide device support")
    ]
    badges_col2 = [
        ("brain", (168, 85, 247), "Local Edge AI Classifiers", "Blocks distracting content"),
        ("lock", (249, 115, 22), "Anti-Bypass App Vault", "Prevents tampering"),
        ("code", (20, 184, 166), "GPL-3.0 Open Source", "Transparent & community driven")
    ]
    
    card_w = 425
    card_h = 74
    gap_x = 22
    gap_y = 16
    
    start_y = curr_y
    for i in range(3):
        by = start_y + (i * (card_h + gap_y))
        # Col 1
        icon_type, icon_bg, title, sub = badges_col1[i]
        draw_rich_badge(draw, left_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=True)
        # Col 2
        icon_type, icon_bg, title, sub = badges_col2[i]
        draw_rich_badge(draw, left_x + card_w + gap_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=True)

    # RIGHT SIDE: Staggered phone mockups with REAL screenshots
    attach_dir = 'd:/Halanoi_Fresh/HalanoiApp/attachments'
    phone_vault = make_phone_mockup(os.path.join(attach_dir, '1000008437.jpg'), target_height=750, dark_theme=True)
    phone_ai = make_phone_mockup(os.path.join(attach_dir, '1000008439.jpg'), target_height=750, dark_theme=True)
    phone_main = make_phone_mockup(os.path.join(attach_dir, '1000008436.jpg'), target_height=860, dark_theme=True)
    
    banner.paste(phone_vault, (1160, 100), phone_vault)
    banner.paste(phone_ai, (1780, 100), phone_ai)
    banner.paste(phone_main, (1430, 160), phone_main)
    
    out_jpg = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner.jpg'
    out_png = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner.png'
    
    banner_rgb = banner.convert('RGB')
    banner_rgb.save(out_jpg, quality=95, optimize=True)
    banner.save(out_png)
    print("Dark banner regenerated successfully!")

def build_light_banner():
    canvas_w, canvas_h = 2400, 1200
    light_bg = (248, 249, 251)
    
    banner = Image.new('RGBA', (canvas_w, canvas_h), (*light_bg, 255))
    
    glow = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    g_draw = ImageDraw.Draw(glow)
    g_draw.ellipse([(1100, 100), (2300, 1100)], fill=(16, 185, 129, 22))
    g_draw.ellipse([(1300, 200), (2200, 900)], fill=(59, 130, 246, 20))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=180))
    banner = Image.alpha_composite(banner, glow)
    
    draw = ImageDraw.Draw(banner)
    
    font_bold = "C:/Windows/Fonts/segoeuib.ttf"
    font_reg = "C:/Windows/Fonts/segoeui.ttf"
    
    font_main_title = ImageFont.truetype(font_bold, 86)
    font_sub_bold = ImageFont.truetype(font_bold, 44)
    font_desc = ImageFont.truetype(font_reg, 30)
    font_badge_title = ImageFont.truetype(font_bold, 22)
    font_badge_sub = ImageFont.truetype(font_reg, 17)
    
    left_x = 130
    curr_y = 125
    
    # App Logo (ONLY the icon, NO "OPEN-SOURCE SECURITY" pill!)
    icon_path = 'd:/Halanoi_Fresh/HalanoiApp/graphics/icon.png'
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert('RGBA')
        icon = icon.resize((96, 96), Image.Resampling.LANCZOS)
        banner.paste(icon, (left_x, curr_y), icon)
        
    curr_y += 120
    
    draw.text((left_x, curr_y), "Halanoi Sovereign", fill=(15, 23, 42, 255), font=font_main_title)
    curr_y += 112
    
    draw.text((left_x, curr_y), "Not just another focus timer.", fill=(71, 85, 105, 255), font=font_sub_bold)
    curr_y += 56
    draw.text((left_x, curr_y), "Sovereign lockdown for your digital life.", fill=(5, 150, 105, 255), font=font_sub_bold)
    curr_y += 80
    
    desc_lines = [
        "A high-security, bypass-proof focus lockdown system for Android.",
        "Combines OS-Level Device Owner policies with an offline Edge AI",
        "vision sniper and DNS interception to eliminate digital distractions."
    ]
    for line in desc_lines:
        draw.text((left_x, curr_y), line, fill=(100, 116, 139, 255), font=font_desc)
        curr_y += 42
        
    curr_y += 46
    
    badges_col1 = [
        ("shield", (16, 185, 129), "OS-Level Device Owner", "Unbypassable lockdown"),
        ("globe", (14, 165, 233), "Cloudflare Family DNS", "Blocks harmful sites"),
        ("android", (34, 197, 94), "Android 10+ (API 29-36)", "Wide device support")
    ]
    badges_col2 = [
        ("brain", (168, 85, 247), "Local Edge AI Classifiers", "Blocks distracting content"),
        ("lock", (249, 115, 22), "Anti-Bypass App Vault", "Prevents tampering"),
        ("code", (20, 184, 166), "GPL-3.0 Open Source", "Transparent & community driven")
    ]
    
    card_w = 425
    card_h = 74
    gap_x = 22
    gap_y = 16
    
    start_y = curr_y
    for i in range(3):
        by = start_y + (i * (card_h + gap_y))
        icon_type, icon_bg, title, sub = badges_col1[i]
        draw_rich_badge(draw, left_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=False)
        icon_type, icon_bg, title, sub = badges_col2[i]
        draw_rich_badge(draw, left_x + card_w + gap_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=False)
        
    attach_dir = 'd:/Halanoi_Fresh/HalanoiApp/attachments'
    phone_vault = make_phone_mockup(os.path.join(attach_dir, '1000008445.jpg'), target_height=750, dark_theme=False)
    phone_filters = make_phone_mockup(os.path.join(attach_dir, '1000008446.jpg'), target_height=750, dark_theme=False)
    phone_main = make_phone_mockup(os.path.join(attach_dir, '1000008444.jpg'), target_height=860, dark_theme=False)
    
    banner.paste(phone_vault, (1160, 100), phone_vault)
    banner.paste(phone_filters, (1780, 100), phone_filters)
    banner.paste(phone_main, (1430, 160), phone_main)
    
    out_jpg = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner_light.jpg'
    out_png = 'd:/Halanoi_Fresh/HalanoiApp/graphics/hero_banner_light.png'
    
    banner_rgb = banner.convert('RGB')
    banner_rgb.save(out_jpg, quality=95, optimize=True)
    banner.save(out_png)
    print("Light banner regenerated successfully!")

if __name__ == '__main__':
    build_dark_banner()
    build_light_banner()
