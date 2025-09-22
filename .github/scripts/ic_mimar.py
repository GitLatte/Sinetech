import json
import os
from datetime import datetime, timezone, timedelta

PLUGINS_JSON_PATH = "builds/plugins.json"
LAST_PLUGINS_JSON_PATH = ".github/scripts/last_plugins.json"
README_PATH = "README.md"

LANGUAGE_FLAGS = {
    "EN": "İngilizce",
    "TR": "Türkçe",
    "FR": "🇫🇷",
    "ES": "🇪🇸",
    "DE": "Almanca",
}

def load_plugins(path):
    if not os.path.exists(path):
        print(f"Uyarı: {path} dosyası bulunamadı.")
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        print(f"\nHATA: {path} dosyası bozuk veya eksik!")
        print(f"Detay: {e}")
        return None

def save_last_plugins(plugins):
    os.makedirs(os.path.dirname(LAST_PLUGINS_JSON_PATH), exist_ok=True)
    with open(LAST_PLUGINS_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(plugins, f, ensure_ascii=False, indent=2)

def generate_version_badge(version):
    version_str = str(version)  # Version'ı string'e çeviriyoruz
    if version_str.startswith("1."):
        color = "green"
    elif version_str.startswith("2."):
        color = "blue"
    else:
        color = "red"
    return f"![](https://img.shields.io/badge/version-{version_str}-{color})"

def find_changes(current, last):
    last_dict = {p["internalName"]: p for p in last}
    changes = []
    three_days_ago = datetime.now(timezone.utc) - timedelta(days=3)

    for plugin in current:
        name = plugin["internalName"]
        last_updated = last_dict.get(name, {}).get("lastUpdated", "")
        last_updated_dt = datetime.fromisoformat(last_updated) if last_updated else None

        if name not in last_dict or (last_updated_dt and last_updated_dt >= three_days_ago):
            changes.append(("🥳 Yeni/Güncellenmiş Eklenti (Son 3 gün)", plugin))
    
    return changes

def generate_changelog(changes):
    if not changes:
        return "### Eklentilerin güncel durumları:\n"
    lines = ["### Son 3 Gün İçindeki Değişiklikler\n"]
    for typ, plugin in changes:
        lines.append(f"- **{plugin['name']}** v{plugin['version']} ({plugin.get('language','N/A').upper()}) - {typ}")
    lines.append("")
    return "\n".join(lines)

def generate_table(plugins):
    lines = []
    lines.append("## Tüm Eklentiler\n")
    lines.append("| Görsel | Eklenti Adı | Version | Dil | Geliştirici | Açıklama |")
    lines.append("|--------|-------------|---------|-----|-------------|----------|")
    for ext in sorted(plugins, key=lambda x: x["name"].lower()):
        icon_url = ext.get("iconUrl", "")
        name = ext.get("name", "Bilinmiyor")
        if ext.get("status") == 0:
             name += " (🚧 Bakım gerekiyor)"        
        version = ext.get("version", "N/A")
        version_badge = generate_version_badge(str(version))
        language = ext.get("language", "N/A").upper()
        flag = LANGUAGE_FLAGS.get(language, "🏳️")
        authors = ", ".join(ext.get("authors", ["Bilinmiyor"]))
        description = ext.get("description", "Açıklama eklenmemiş.").replace('\n', ' ').replace('|', '\|').replace('\r', '')
        lines.append(f"| ![]({icon_url}) | {name} | {version_badge} | {flag} | {authors} | {description} |")
    lines.append("")
    return "\n".join(lines)

def main():
    plugins = load_plugins(PLUGINS_JSON_PATH)
    if plugins is None:
        print("İşlem durdu: plugins.json dosyası düzgün değil veya eksik.")
        return

    last_plugins = load_plugins(LAST_PLUGINS_JSON_PATH) or []
    changes = find_changes(plugins, last_plugins)

    readme = [
        "## ☁️ Latte - Sinetech CloudStream Eklenti Listesi ve Değişiklik Günlüğü",
        f"**Son Güncelleme:** {(datetime.now(timezone.utc) + timedelta(hours=3)).strftime('%d %B %Y %A - %H:%M:%S')}".replace('January', 'Ocak').replace('February', 'Şubat').replace('March', 'Mart').replace('April', 'Nisan').replace('May', 'Mayıs').replace('June', 'Haziran').replace('July', 'Temmuz').replace('August', 'Ağustos').replace('September', 'Eylül').replace('October', 'Ekim').replace('November', 'Kasım').replace('December', 'Aralık').replace('Monday', 'Pazartesi').replace('Tuesday', 'Salı').replace('Wednesday', 'Çarşamba').replace('Thursday', 'Perşembe').replace('Friday', 'Cuma').replace('Saturday', 'Cumartesi').replace('Sunday', 'Pazar'),
        "",
        "Bu depo tüm Latte - Sinetech eklentilerini ve en son güncellemeleri içerir.",
        "",
        generate_changelog(changes),
        "---",
        generate_table(plugins)
    ]      
    readme.append("---")
    readme.append("## 🤝 Katkıda Bulunanlar\n")
    readme.append("| Geliştirici | Rol |")
    readme.append("|-------------|-----|")
    readme.append("| [GitLatte](https://github.com/GitLatte) | Depo Geliştiricisi |")
    readme.append("| [patr0nq](https://github.com/patr0nq) | Güncelleme ve Geliştirme Ortağı |")
    readme.append("| [keyiflerolsun](https://github.com/keyiflerolsun) | Eklenti kodları ilham kaynağı |")
    readme.append("| [feroxx](https://github.com/feroxx) | Youtube videolarının oynatılmasını sağlayan geliştirme sahibi |")    
    readme.append("| [doGior](https://github.com/DoGior) | Eklenti kodları ilham kaynağı |")
    readme.append("| [powerboard](https://forum.sinetech.tr/uye/powerboard.3822/) | PowerDizi-PowerSinema liste sahibi |")
    readme.append("| [tıngırmıngır](https://forum.sinetech.tr/uye/tingirmingir.137/) | TMDB ve Tv Bahçesi ilham kaynağı |")
    readme.append("| [mooncrown](https://forum.sinetech.tr/uye/mooncrown.10472/) | Sinema/Dizi eklentisi \"İzlemeye Devam Et\" başlatma sebebi |")
    readme.append("| [nedirne](https://forum.sinetech.tr/uye/nedirne.13409/) | Sinema/Dizi eklentisi TMDB olayını başlatma sebebi |")
    readme.append("| [Memetcandal](https://forum.sinetech.tr/uye/memetcandal.306/) | Aniworld eklentisini Hexated deposundan alıp düzenleme sebebi |")
    readme.append("| [fsozkan](https://forum.sinetech.tr/uye/fsozkan.14502/) | KickTR eklentisinin çıkış sebebi |")
    readme.append("\n📬 **Destek İletişim:** [Latte](https://forum.sinetech.tr/konu/powerboard-film-ve-dizi-arsivine-ozel-cloudstream-deposu.3672/)")
    readme.append("---")
    readme.append("🔔 **Not:** Repoyu eklemek için Cloudstream içerisindeki Depo Ekle alanında Depo URL kısmına **\"Latte\"** veya **sinetech** yazmanız yeterlidir.")

    with open(README_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(readme))
    print("README.md başarıyla güncellendi!")

    save_last_plugins(plugins)

if __name__ == "__main__":
    main()
