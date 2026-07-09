import os
import re
import subprocess

def main():
    export_file = r"d:\Halanoi_Fresh\HalanoiApp\export.txt"
    if not os.path.exists(export_file):
        print(f"Error: {export_file} not found!")
        return

    with open(export_file, "r", encoding="utf-8") as f:
        content = f.read()

    # Parse websites
    websites = []
    website_sec = re.search(r"=== CUSTOM BLOCKED WEBSITES ===\n(.*?)(?=\n===|$)", content, re.DOTALL)
    if website_sec:
        for line in website_sec.group(1).split("\n"):
            line = line.strip()
            if line and not line.startswith("==="):
                # remove http/https
                line = re.sub(r"^https?://", "", line)
                # remove trailing slash
                line = line.rstrip("/")
                if line:
                    websites.append(line)

    # Parse keywords
    keywords = []
    keyword_sec = re.search(r"=== CUSTOM BLOCKED KEYWORDS ===\n(.*?)(?=\n===|$)", content, re.DOTALL)
    if keyword_sec:
        raw_keywords = keyword_sec.group(1).strip()
        for kw in raw_keywords.split(","):
            kw = kw.strip().lower()
            if kw:
                keywords.append(kw)

    # Parse apps
    apps = []
    apps_sec = re.search(r"=== LOCKED APPS ===\n(.*?)(?=\n===|$)", content, re.DOTALL)
    if apps_sec:
        for line in apps_sec.group(1).split("\n"):
            line = line.strip()
            if line and not line.startswith("==="):
                apps.append(line)

    # Generate XML
    xml_lines = [
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>",
        "<map>"
    ]

    if websites:
        xml_lines.append('    <set name="CUSTOM_SITES">')
        for site in websites:
            xml_lines.append(f'        <string>{site}</string>')
        xml_lines.append('    </set>')

    if keywords:
        xml_lines.append('    <set name="CUSTOM_KEYWORDS">')
        for kw in keywords:
            xml_lines.append(f'        <string>{kw}</string>')
        xml_lines.append('    </set>')

    if apps:
        xml_lines.append('    <set name="LOCKED_APPS">')
        for app in apps:
            xml_lines.append(f'        <string>{app}</string>')
        xml_lines.append('    </set>')

    xml_lines.append("</map>")
    xml_content = "\n".join(xml_lines)

    temp_xml_path = "HalanoiVault.xml"
    with open(temp_xml_path, "w", encoding="utf-8") as f:
        f.write(xml_content)

    print(f"Generated {temp_xml_path} successfully.")
    print(f"Blocked Sites: {len(websites)}")
    print(f"Blocked Keywords: {len(keywords)}")
    print(f"Locked Apps: {len(apps)}")

    # Run ADB commands to push and copy
    try:
        # Check connected devices
        devices_out = subprocess.check_output("adb devices", shell=True).decode()
        if "device\r\n" not in devices_out and "device\n" not in devices_out:
            print("Error: No ADB device connected! Please plug in your phone.")
            return

        print("Pushing preferences to device...")
        subprocess.check_call("adb push HalanoiVault.xml /data/local/tmp/HalanoiVault.xml", shell=True)
        subprocess.check_call('adb shell "run-as com.halanoi.app mkdir -p shared_prefs"', shell=True)
        subprocess.check_call('adb shell "run-as com.halanoi.app cp /data/local/tmp/HalanoiVault.xml shared_prefs/HalanoiVault.xml"', shell=True)
        subprocess.check_call('adb shell "run-as com.halanoi.app chmod 660 shared_prefs/HalanoiVault.xml"', shell=True)
        
        # Kill the app so it reloads preference file next launch
        subprocess.check_call("adb shell am force-stop com.halanoi.app", shell=True)
        
        # Clean up local file
        os.remove(temp_xml_path)
        print("SUCCESS! All websites, keywords, and apps imported successfully into the Halanoi App.")
    except Exception as e:
        print(f"Error executing ADB commands: {e}")

if __name__ == "__main__":
    main()
