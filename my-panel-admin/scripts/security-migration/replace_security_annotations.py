import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2] / "src" / "main" / "java"

PREAUTHORIZE_IMPORT = "import org.springframework.security.access.prepost.PreAuthorize;"
REQUIRE_PERMISSION_IMPORT = "import com.cq.panel.authlite.annotation.RequirePermission;"


def transform(text: str) -> str:
    text = text.replace(PREAUTHORIZE_IMPORT, REQUIRE_PERMISSION_IMPORT)
    pattern = re.compile(r'@PreAuthorize\("@ss\.hasPermi\(\'([^\']+)\'\)"\)')
    text = pattern.sub(r'@RequirePermission("\1")', text)
    return text


def main() -> int:
    java_files = list(ROOT.rglob("*.java"))
    changed = []
    for p in java_files:
        original = p.read_text(encoding="utf-8")
        updated = transform(original)
        if updated != original:
            p.write_text(updated, encoding="utf-8")
            changed.append(p)

    log_path = Path(__file__).with_suffix(".log")
    log_path.write_text("\n".join(str(p.relative_to(ROOT)) for p in changed) + ("\n" if changed else ""), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

