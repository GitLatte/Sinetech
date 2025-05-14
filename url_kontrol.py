# URL kontrol edip değişeni almaya çalışıyoruz.
import os
import re
import logging
import requests # requests.exceptions.RequestException için eklendi
from urllib.parse import urlparse
import cloudscraper # Cloudflare korumasını geçmek için requests yerine

# Basit loglama ayarları
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Kontrol edilecek domain yapısı örneği (örn: https://basename<rakam>.domain)
DOMAIN_PATTERN = re.compile(r'https?://([a-zA-Z\-]+)(\d+)(\.[a-zA-Z.]+/?.*)')
# Kotlin dosyasında mainUrl bulma örneği
MAIN_URL_PATTERN = re.compile(r'override\s+var\s+mainUrl\s*=\s*"([^"]+)"')

# Kaç sonraki domain denenecek
MAX_URL_CHECK_ATTEMPTS = 10
# İstekler için timeout (saniye)
REQUEST_TIMEOUT = 8

# Kontrol dışı bırakılacak klasörler
EXCLUDED_FOLDERS = {
    ".git", ".github", "img", "gradle", "build", "__pycache__",
    # Otomatik URL kontrolü gerektirmeyen eklentiler
    "powerDizi", "powerSinema", "YTEglence", "YTCanliTV",
    "TvBahcesi", "TOGrup", "DDizi", "DaddyLive"
}

def find_kt_file(plugin_dir):
    plugin_name = os.path.basename(plugin_dir)
    expected_file = f"{plugin_name}.kt"
    kotlin_path = os.path.join(plugin_dir, 'src', 'main', 'kotlin')
    if os.path.isdir(kotlin_path):
        for root, _, files in os.walk(kotlin_path):
            if expected_file in files:
                return os.path.join(root, expected_file)
    if os.path.exists(os.path.join(plugin_dir, expected_file)):
         return os.path.join(plugin_dir, expected_file)
    logging.warning(f"[{plugin_name}] Ana Kotlin dosyası bulunamadı: {expected_file}")
    return None

def get_main_url(kt_file_path):
    try:
        with open(kt_file_path, "r", encoding="utf-8") as f:
            content = f.read()
            match = MAIN_URL_PATTERN.search(content)
            if match:
                return match.group(1)
    except Exception as e:
        logging.error(f"Dosya okuma hatası {kt_file_path}: {e}")
    return None

def update_main_url(kt_file_path, old_url, new_url):
    try:
        with open(kt_file_path, "r", encoding="utf-8") as f:
            content = f.read()
        old_line = f'override var mainUrl = "{old_url}"'
        new_line = f'override var mainUrl = "{new_url}"'
        if old_line not in content:
             logging.warning(f"[{os.path.basename(os.path.dirname(kt_file_path))}] Eski URL satırı bulunamadı: {old_line}")

             return False
        new_content = content.replace(old_line, new_line, 1)
        with open(kt_file_path, "w", encoding="utf-8") as f:
            f.write(new_content)
        logging.info(f"[{os.path.basename(os.path.dirname(kt_file_path))}] mainUrl güncellendi: {old_url} -> {new_url}")
        return True
    except Exception as e:
        logging.error(f"Dosya yazma hatası {kt_file_path}: {e}")
        return False

def check_url_working(url, scraper):
    try:
        logging.info(f"    -> Deneniyor: {url}")
        # Cloudscraper ile GET isteği
        response = scraper.get(url, timeout=REQUEST_TIMEOUT, allow_redirects=True)
        response.raise_for_status() # Hata kodu varsa exception fırlat

        content_ok = "dizifun" in response.text.lower() and "<html>" in response.text.lower() and "<body>" in response.text.lower()

        if content_ok:
            final_url = response.url.strip('/')
            logging.info(f"    [+] Çalışıyor: {final_url}")
            return final_url
        else:
            logging.warning(f"    [-] İçerik Uymuyor: {url} (Durum Kodu: {response.status_code})")
            return None
    except requests.exceptions.RequestException as e:
        logging.error(f"    [!] İstek Hatası: {url} - {e}")
        return None
    except Exception as e:
        logging.error(f"    [!] Beklenmedik Hata: {url} - {e}")
        return None

def main():
    logging.info("URL Güncelleyici Başlatıldı.")
    base_dir = "."
    scraper = cloudscraper.create_scraper()
    updated_plugins = []

    plugin_folders = sorted([
        f for f in os.listdir(base_dir)
        if os.path.isdir(os.path.join(base_dir, f)) and f not in EXCLUDED_FOLDERS
    ])

    logging.info(f"Kontrol edilecek eklenti klasörleri: {plugin_folders}")

    for plugin_name in plugin_folders:
        plugin_dir = os.path.join(base_dir, plugin_name)
        kt_file = find_kt_file(plugin_dir)
        if not kt_file:
            continue

        logging.info(f"--- [{plugin_name}] Kontrol Ediliyor ---")
        current_url = get_main_url(kt_file)
        if not current_url:
            logging.warning(f"[{plugin_name}] mainUrl bulunamadı.")
            continue

        domain_match = DOMAIN_PATTERN.match(current_url)
        if not domain_match:
            logging.info(f"[{plugin_name}] URL yapısı otomatik kontrole uygun değil, atlanıyor: {current_url}")
            continue

        new_working_url = check_url_working(current_url, scraper)

        if not new_working_url:
            logging.warning(f"[{plugin_name}] Mevcut URL çalışmıyor: {current_url}")
            base_name, current_number_str, domain_suffix_part = domain_match.groups()
            current_number = int(current_number_str)
            # Domain kısmını düzelt (sadece .com, .org vb. kısmı al)
            domain_only_match = re.match(r'(\.[a-zA-Z.]+)', domain_suffix_part)
            domain = domain_only_match.group(1) if domain_only_match else ".com"

            for i in range(1, MAX_URL_CHECK_ATTEMPTS + 1):
                next_number = current_number + i
                next_url = f"https://{base_name}{next_number}{domain}"
                logging.info(f"[{plugin_name}] Yeni URL deneniyor: {next_url}")
                new_working_url = check_url_working(next_url, scraper)
                if new_working_url:
                    break
            if not new_working_url:
                 logging.error(f"[{plugin_name}] Çalışan yeni URL bulunamadı.")

        if new_working_url and new_working_url != current_url:
            logging.info(f"[{plugin_name}] Güncelleme yapılacak: {current_url} -> {new_working_url}")
            # Sadece Kotlin dosyasını güncelle
            if update_main_url(kt_file, current_url, new_working_url):
                logging.info(f"[»] Güncellendi       : {current_url} -> {new_working_url}")
                updated_plugins.append(plugin_name)
            else:
                 logging.error(f"[{plugin_name}] Kotlin dosyası güncellenemedi.")
        elif new_working_url:
            logging.info(f"[{plugin_name}] URL güncel: {current_url}")

    logging.info("URL Kontrolü Tamamlandı.")
    if updated_plugins:
        logging.info(f"Güncellenen eklentiler: {', '.join(updated_plugins)}")
    else:
        logging.info("Güncellenecek eklenti bulunmadı.")


if __name__ == "__main__":
    main()
