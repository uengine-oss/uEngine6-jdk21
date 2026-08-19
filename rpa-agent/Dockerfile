# Server-side RPA worker image
#
#   docker build -t uengine/rpa-worker .
#   docker run -e UENGINE_BASE_URL=http://host.docker.internal:9094 uengine/rpa-worker
#
# Xvfb 가상 화면(:99) 위에서 헤드풀 브라우저를 띄우고, runner 가 ffmpeg x11grab 으로
# 화면 전체를 녹화(실행이력 영상) + 1초 프레임 업로드(라이브 스트리밍)한다.
FROM python:3.11-slim

WORKDIR /app

# xvfb: 가상 디스플레이, ffmpeg: 화면 녹화/프레임 캡처
RUN apt-get update \
    && apt-get install -y --no-install-recommends xvfb ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && (pip install --no-cache-dir rpaframework || true) \
    && playwright install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/*

COPY uengine_rpa/ uengine_rpa/
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

ENV UENGINE_BASE_URL=http://host.docker.internal:9094 \
    UENGINE_POLL_INTERVAL=3 \
    PYTHONUNBUFFERED=1 \
    DISPLAY=:99 \
    UENGINE_SCREEN_SIZE=1280x800 \
    UENGINE_RPA_HEADFUL=1

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["python", "-m", "uengine_rpa.worker"]
