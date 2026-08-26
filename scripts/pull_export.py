"""
에뮬레이터의 /sdcard/Download 에서 앱이 내보낸 xlsx 중 가장 최근 파일을
바탕화면으로 꺼내온다. 그냥 실행만 하면 됨:

    python scripts/pull_export.py
"""
import subprocess
import re

ADB = r"C:\Users\PARK\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE_DIR = "/sdcard/Download"
# adb가 콘솔 코드페이지 때문에 한글 경로를 깨트리는 경우가 있어 파일명은 영문으로 둔다.
DEST_PATH = r"C:\Users\PARK\Desktop\workout_export.xlsx"


def run(*args: str) -> str:
    result = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed:\n{result.stderr}")
    return result.stdout


def find_latest_xlsx() -> str:
    listing = run("shell", "ls", "-1", DEVICE_DIR)
    names = [line.strip() for line in listing.splitlines() if line.strip().endswith(".xlsx")]
    if not names:
        raise SystemExit(f"{DEVICE_DIR} 에 xlsx 파일이 없습니다. 앱에서 먼저 내보내세요.")

    # 파일명이 운동일지_YYYYMMDD_HHMMSS.xlsx 형식이므로 이름 정렬이 곧 시간 정렬이다.
    def sort_key(name: str):
        m = re.search(r"(\d{8}_\d{6})", name)
        return m.group(1) if m else name

    names.sort(key=sort_key)
    return names[-1]


def main() -> None:
    latest = find_latest_xlsx()
    print(f"최신 파일: {latest}")
    run("pull", f"{DEVICE_DIR}/{latest}", DEST_PATH)
    print(f"저장 완료: {DEST_PATH}")


if __name__ == "__main__":
    main()
