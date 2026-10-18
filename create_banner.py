import os
import fitz
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# Official Google Material Design Icons (Round style, Apache 2.0 / FOSS)
MATERIAL_SVGS = {
    "shield": '<path d="m11.19 1.36-7 3.11C3.47 4.79 3 5.51 3 6.3V11c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V6.3c0-.79-.47-1.51-1.19-1.83l-7-3.11c-.51-.23-1.11-.23-1.62 0zm-1.9 14.93L6.7 13.7a.996.996 0 1 1 1.41-1.41L10 14.17l5.88-5.88a.996.996 0 1 1 1.41 1.41l-6.59 6.59a.996.996 0 0 1-1.41 0z" fill="#FFFFFF"/>',
    "globe": '<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" fill="#FFFFFF"/>',
    "android": '<path d="m17.6 9.48 1.84-3.18c.16-.31.04-.69-.26-.85a.637.637 0 0 0-.83.22l-1.88 3.24a11.463 11.463 0 0 0-8.94 0L5.65 5.67a.643.643 0 0 0-.87-.2c-.28.18-.37.54-.22.83L6.4 9.48A10.78 10.78 0 0 0 1 18h22a10.78 10.78 0 0 0-5.4-8.52zM7 15.25a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5zm10 0a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5z" fill="#FFFFFF"/>',
    "brain": '<path d="M13 8.57a1.43 1.43 0 1 0 0 2.86 1.43 1.43 0 0 0 0-2.86z" fill="#FFFFFF"/><path d="M13.21 3c-3.84-.11-7 2.87-7.19 6.64L4.1 12.2a.5.5 0 0 0 .4.8H6v3c0 1.1.9 2 2 2h1v2c0 .55.45 1 1 1h5c.55 0 1-.45 1-1v-3.68a7.004 7.004 0 0 0 4-6.58c-.14-3.62-3.18-6.63-6.79-6.74zM16 10c0 .13-.01.26-.02.39l.83.66c.08.06.1.16.05.25l-.8 1.39c-.05.09-.16.12-.24.09l-.99-.4c-.21.16-.43.29-.67.39L14 13.83c-.01.1-.1.17-.2.17h-1.6c-.1 0-.18-.07-.2-.17l-.15-1.06c-.25-.1-.47-.23-.68-.39l-.99.4c-.09.03-.2 0-.25-.09l-.8-1.39a.19.19 0 0 1 .05-.25l.84-.66c-.01-.13-.02-.26-.02-.39s.02-.27.04-.39l-.85-.66c-.08-.06-.1-.16-.05-.26l.8-1.38c.05-.09.15-.12.24-.09l1 .4c.2-.15.43-.29.67-.39L12 6.17c.02-.1.1-.17.2-.17h1.6c.1 0 .18.07.2.17l.15 1.06c.24.1.46.23.67.39l1-.4c.09-.03.2 0 .24.09l.8 1.38a.2.2 0 0 1-.05.26l-.85.66c.03.12.04.25.04.39z" fill="#FFFFFF"/>',
    "lock": '<path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM9 8V6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9z" fill="#FFFFFF"/>',
    "code": '<path d="M8.7 15.9 4.8 12l3.9-3.9a.984.984 0 0 0 0-1.4.984.984 0 0 0-1.4 0l-4.59 4.59a.996.996 0 0 0 0 1.41l4.59 4.6c.39.39 1.01.39 1.4 0a.984.984 0 0 0 0-1.4zm6.6 0 3.9-3.9-3.9-3.9a.984.984 0 0 1 0-1.4.984.984 0 0 1 1.4 0l4.59 4.59c.39.39.39 1.02 0 1.41l-4.59 4.6a.984.984 0 0 1-1.4 0 .984.984 0 0 1 0-1.4z" fill="#FFFFFF"/>'
}

def render_material_icon(icon_name, size=30):
    svg_paths = MATERIAL_SVGS[icon_name]
    svg_str = f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 24 24">{svg_paths}</svg>'
    doc = fitz.open("svg", svg_str.encode("utf-8"))
    mat = fitz.Matrix(3.0, 3.0)  # 3x supersampling for razor sharp vector rendering
    pix = doc[0].get_pixmap(matrix=mat, alpha=True)
    img = Image.frombytes("RGBA", [pix.width, pix.height], pix.samples)
    return img.resize((size, size), Image.Resampling.LANCZOS)

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
    
    screen_mask = create_rounded_mask((screen_w, screen_h), radius=screen_radius)
    phone.paste(screen_resized, (bezel, bezel), screen_mask)
    
    camera_radius = 6
    cam_x = phone_w // 2
    cam_y = bezel + 12
    draw.ellipse([(cam_x - camera_radius, cam_y - camera_radius), (cam_x + camera_radius, cam_y + camera_radius)], fill=(8, 10, 14, 255))
    
    pad = 80
    shadow_canvas = Image.new('RGBA', (phone_w + pad * 2, phone_h + pad * 2), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow_canvas)
    
    s_color = (0, 0, 0, 140) if dark_theme else (20, 30, 50, 60)
    s_draw.rounded_rectangle([(pad, pad + 25), (pad + phone_w, pad + phone_h + 25)], radius=corner_radius, fill=s_color)
    shadow_canvas = shadow_canvas.filter(ImageFilter.GaussianBlur(radius=28))
    
    shadow_canvas.paste(phone, (pad, pad), phone)
    return shadow_canvas

def draw_rich_badge(banner, draw, x, y, w, h, icon_type, icon_bg, title, subtitle, font_title, font_sub, dark_theme=True):
    if dark_theme:
        card_bg = (16, 23, 35, 240)
        card_border = (38, 52, 75, 180)
        title_color = (255, 255, 255, 255)
        sub_color = (148, 163, 184, 255)
    else:
        card_bg = (255, 255, 255, 240)
        card_border = (220, 230, 242, 220)
        title_color = (15, 23, 42, 255)
        sub_color = (100, 116, 139, 255)

    draw.rounded_rectangle([(x, y), (x + w, y + h)], radius=h // 2, fill=card_bg, outline=card_border, width=1)
    
    circle_d = 52
    circle_x = x + 12
    circle_y = y + (h - circle_d) // 2
    draw.ellipse([(circle_x, circle_y), (circle_x + circle_d, circle_y + circle_d)], fill=icon_bg)
    
    # Render official Material Icon
    icon_img = render_material_icon(icon_type, size=30)
    ix = circle_x + (circle_d - icon_img.width) // 2
    iy = circle_y + (circle_d - icon_img.height) // 2
    banner.paste(icon_img, (ix, iy), icon_img)
    
    text_x = circle_x + circle_d + 16
    draw.text((text_x, y + 13), title, fill=title_color, font=font_title)
    draw.text((text_x, y + 41), subtitle, fill=sub_color, font=font_sub)

def build_dark_banner():
    canvas_w, canvas_h = 2400, 1200
    dark_bg = (10, 14, 23)
    
    banner = Image.new('RGBA', (canvas_w, canvas_h), (*dark_bg, 255))
    
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
    
    icon_path = 'd:/Halanoi_Fresh/HalanoiApp/graphics/icon.png'
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert('RGBA')
        icon = icon.resize((96, 96), Image.Resampling.LANCZOS)
        banner.paste(icon, (left_x, curr_y), icon)
        
    curr_y += 120
    
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
        draw_rich_badge(banner, draw, left_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=True)
        icon_type, icon_bg, title, sub = badges_col2[i]
        draw_rich_badge(banner, draw, left_x + card_w + gap_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=True)

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
    print("Dark banner regenerated successfully with official Material Icons!")

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
        draw_rich_badge(banner, draw, left_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=False)
        icon_type, icon_bg, title, sub = badges_col2[i]
        draw_rich_badge(banner, draw, left_x + card_w + gap_x, by, card_w, card_h, icon_type, icon_bg, title, sub, font_badge_title, font_badge_sub, dark_theme=False)
        
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
    print("Light banner regenerated successfully with official Material Icons!")

if __name__ == '__main__':
    build_dark_banner()
    build_light_banner()
