#!/usr/bin/env python3
"""Render a short queuectl workflow animation for LinkedIn posts.

The renderer is dependency-free and uses ffmpeg's drawbox/drawtext filters so
the resulting animation stays editable from source control.
"""

from __future__ import annotations

import subprocess
from pathlib import Path


WIDTH = 1280
HEIGHT = 720
DURATION = 10
FPS = 24

ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = ROOT / "docs" / "assets"
GRAPH_PATH = ASSETS_DIR / "queuectl-linkedin.filtergraph"
MP4_PATH = ASSETS_DIR / "queuectl-linkedin.mp4"
GIF_PATH = ASSETS_DIR / "queuectl-linkedin.gif"
POSTER_PATH = ASSETS_DIR / "queuectl-linkedin-poster.png"

TITLE_FONT = Path("/System/Library/Fonts/Supplemental/Arial Bold.ttf")
BODY_FONT = Path("/System/Library/Fonts/Supplemental/Arial.ttf")
MONO_FONT = Path("/System/Library/Fonts/Supplemental/Andale Mono.ttf")

BG = "0xf7f2e8"
CARD = "0xfffaf2@0.96"
LINE = "0xe6d7bf@1.0"
INK = "0x1f1f1f"
MUTED = "0x6b6459"
ACCENT = "0x0f766e"
ACCENT_SOFT = "0xcffafe@0.65"
WARN = "0xb45309"
WARN_SOFT = "0xfef3c7@0.78"
ERROR = "0xb91c1c"
ERROR_SOFT = "0xfee2e2@0.78"
OK = "0x166534"
OK_SOFT = "0xdcfce7@0.82"
SHADOW = "0x19140a@0.10"
GOLD_WASH = "0xf7d8a6@0.35"
GREEN_WASH = "0xbce7d5@0.35"


def esc(value: str) -> str:
  return (
      value.replace("\\", "\\\\")
      .replace(":", r"\:")
      .replace("'", r"\'")
      .replace(",", r"\,")
      .replace("%", r"\%")
  )


def enable_window(start: float | None, end: float | None) -> str | None:
  if start is None and end is None:
    return None
  if start is None or end is None:
    raise ValueError("enable windows require both start and end")
  return f"between(t,{start:.2f},{end:.2f})"


def drawbox(
    x: str | int,
    y: str | int,
    w: str | int,
    h: str | int,
    color: str,
    *,
    thickness: str | int = "fill",
    start: float | None = None,
    end: float | None = None,
) -> str:
  parts = [
      "drawbox",
      f"x={x}",
      f"y={y}",
      f"w={w}",
      f"h={h}",
      f"color={color}",
      f"t={thickness}",
  ]
  enabled = enable_window(start, end)
  if enabled:
    parts.append(f"enable='{enabled}'")
  return f"{parts[0]}=" + ":".join(parts[1:])


def drawtext(
    text: str,
    x: str | int,
    y: str | int,
    *,
    size: int,
    color: str,
    font: Path,
    start: float | None = None,
    end: float | None = None,
    box_color: str | None = None,
    box_border: int = 18,
    line_spacing: int | None = None,
) -> str:
  parts = [
      "drawtext",
      f"fontfile='{esc(font.as_posix())}'",
      f"text='{esc(text)}'",
      f"x={x}",
      f"y={y}",
      f"fontsize={size}",
      f"fontcolor={color}",
  ]
  if box_color:
    parts.extend(
        [
            "box=1",
            f"boxcolor={box_color}",
            f"boxborderw={box_border}",
        ]
    )
  if line_spacing is not None:
    parts.append(f"line_spacing={line_spacing}")
  enabled = enable_window(start, end)
  if enabled:
    parts.append(f"enable='{enabled}'")
  return f"{parts[0]}=" + ":".join(parts[1:])


def panel(filters: list[str], x: int, y: int, w: int, h: int) -> None:
  filters.append(drawbox(x + 10, y + 12, w, h, SHADOW))
  filters.append(drawbox(x, y, w, h, CARD))
  filters.append(drawbox(x, y, w, h, LINE, thickness=3))


def highlight(
    filters: list[str],
    x: int,
    y: int,
    w: int,
    h: int,
    color: str,
    start: float,
    end: float,
) -> None:
  filters.append(drawbox(x - 6, y - 6, w + 12, h + 12, f"{color}@0.18", start=start, end=end))
  filters.append(drawbox(x - 6, y - 6, w + 12, h + 12, color, thickness=6, start=start, end=end))


def chip(
    filters: list[str],
    x: int,
    y: int,
    w: int,
    h: int,
    fill: str,
    text: str,
    text_color: str,
    *,
    size: int = 18,
    start: float | None = None,
    end: float | None = None,
) -> None:
  filters.append(drawbox(x, y, w, h, fill, start=start, end=end))
  filters.append(drawbox(x, y, w, h, LINE, thickness=2, start=start, end=end))
  filters.append(
      drawtext(
          text,
          x + 12,
          y + 8,
          size=size,
          color=text_color,
          font=TITLE_FONT,
          start=start,
          end=end,
      )
  )


def token_move(
    filters: list[str],
    *,
    color: str,
    border: str,
    x1: int,
    y1: int,
    x2: int,
    y2: int,
    start: float,
    end: float,
    size: int = 26,
) -> None:
  duration = end - start
  steps = max(3, min(8, round(duration * 6)))
  window = duration / steps
  for index in range(steps):
    progress = (index + 1) / steps
    x = round(x1 + (x2 - x1) * progress)
    y = round(y1 + (y2 - y1) * progress)
    phase_start = start + index * window
    phase_end = start + (index + 1) * window
    token_hold(
        filters,
        color=color,
        border=border,
        x=x,
        y=y,
        start=phase_start,
        end=phase_end,
        size=size,
    )


def token_hold(
    filters: list[str],
    *,
    color: str,
    border: str,
    x: int,
    y: int,
    start: float,
    end: float,
    size: int = 26,
) -> None:
  filters.append(drawbox(x, y, size, size, f"{color}@0.25", start=start, end=end))
  filters.append(drawbox(x, y, size, size, color, start=start, end=end))
  filters.append(drawbox(x, y, size, size, border, thickness=3, start=start, end=end))


def job_card(
    filters: list[str],
    *,
    x: int,
    y: int,
    label: str,
    fill: str,
    border: str,
    text_color: str,
    start: float | None = None,
    end: float | None = None,
    w: int = 138,
    h: int = 34,
) -> None:
  filters.append(drawbox(x, y, w, h, fill, start=start, end=end))
  filters.append(drawbox(x, y, w, h, border, thickness=2, start=start, end=end))
  filters.append(
      drawtext(
          label,
          x + 18,
          y + 8,
          size=18,
          color=text_color,
          font=MONO_FONT,
          start=start,
          end=end,
      )
  )


def move_job_card(
    filters: list[str],
    *,
    label: str,
    fill: str,
    border: str,
    text_color: str,
    x1: int,
    y1: int,
    x2: int,
    y2: int,
    start: float,
    end: float,
    w: int = 138,
    h: int = 34,
) -> None:
  duration = end - start
  steps = max(4, min(9, round(duration * 7)))
  window = duration / steps
  for index in range(steps):
    progress = (index + 1) / steps
    x = round(x1 + (x2 - x1) * progress)
    y = round(y1 + (y2 - y1) * progress)
    phase_start = start + index * window
    phase_end = start + (index + 1) * window
    job_card(
        filters,
        x=x,
        y=y,
        label=label,
        fill=fill,
        border=border,
        text_color=text_color,
        start=phase_start,
        end=phase_end,
        w=w,
        h=h,
    )


def build_filtergraph() -> str:
  filters: list[str] = ["format=rgba"]

  # Atmosphere.
  filters.append(drawbox(930, -40, 360, 210, GOLD_WASH))
  filters.append(drawbox(-100, 590, 360, 175, GREEN_WASH))
  filters.append(drawbox(990, 565, 300, 175, ACCENT_SOFT))

  store = (70, 150, 620, 314)
  workers = (720, 150, 490, 262)
  retry = (720, 420, 235, 140)
  dlq = (975, 420, 235, 140)
  completed = (70, 560, 1140, 90)
  footer = (70, 656, 1140, 38)

  for x, y, w, h in [store, workers, retry, dlq, completed, footer]:
    panel(filters, x, y, w, h)

  filters.append(drawtext("Working of QueueCTL Pro", 70, 58, size=38, color=INK, font=TITLE_FONT))
  filters.append(
      drawtext(
          "Two queues, four jobs, three workers. Three jobs complete directly; one job fails, waits in retry, runs again, then lands in the DLQ.",
          72,
          102,
          size=16,
          color=MUTED,
          font=BODY_FONT,
      )
  )

  filters.append(drawtext("POSTGRESQL JOB STORE", 96, 180, size=24, color=INK, font=TITLE_FONT))
  filters.append(drawtext("Waiting jobs stay here until a worker leases a row.", 98, 210, size=17, color=MUTED, font=BODY_FONT))
  filters.append(drawbox(381, 240, 2, 150, LINE))
  chip(filters, 108, 244, 138, 28, ACCENT_SOFT, "queue: default", ACCENT, size=15)
  chip(filters, 408, 244, 132, 28, WARN_SOFT, "queue: emails", WARN, size=15)

  store_slots = [
      (118, 288, 228, 42),
      (118, 346, 228, 42),
      (418, 288, 228, 42),
      (418, 346, 228, 42),
  ]
  for slot in store_slots:
    filters.append(drawbox(*slot, "0xffffff@0.76"))
    filters.append(drawbox(*slot, LINE, thickness=2))

  filters.append(drawtext("3 WORKERS", 748, 180, size=24, color=INK, font=TITLE_FONT))
  filters.append(drawtext("Each worker handles one leased job at a time.", 750, 210, size=17, color=MUTED, font=BODY_FONT))

  worker_slots = [
      (962, 236, 166, 42),
      (962, 296, 166, 42),
      (962, 356, 166, 42),
  ]
  for slot in worker_slots:
    filters.append(drawbox(*slot, "0xffffff@0.78"))
    filters.append(drawbox(*slot, LINE, thickness=2))

  filters.append(drawtext("worker-1", 772, 248, size=17, color=MUTED, font=MONO_FONT))
  filters.append(drawtext("worker-2", 772, 308, size=17, color=MUTED, font=MONO_FONT))
  filters.append(drawtext("worker-3", 772, 368, size=17, color=MUTED, font=MONO_FONT))

  filters.append(drawtext("RETRY / BACKOFF", 742, 442, size=19, color=INK, font=TITLE_FONT))
  filters.append(drawtext("Failed jobs wait, then run again.", 742, 465, size=13, color=MUTED, font=BODY_FONT))
  filters.append(drawbox(742, 502, 188, 28, "0xffffff@0.76"))
  filters.append(drawbox(742, 502, 188, 28, LINE, thickness=2))

  filters.append(drawtext("DLQ", 997, 442, size=19, color=INK, font=TITLE_FONT))
  filters.append(drawtext("Jobs that still fail end here.", 997, 465, size=13, color=MUTED, font=BODY_FONT))
  filters.append(drawbox(997, 502, 188, 28, "0xffffff@0.76"))
  filters.append(drawbox(997, 502, 188, 28, LINE, thickness=2))

  filters.append(drawtext("COMPLETED", 98, 590, size=24, color=INK, font=TITLE_FONT))
  filters.append(drawtext("Successful jobs land here.", 100, 618, size=16, color=MUTED, font=BODY_FONT))

  completed_slots = [
      (430, 579, 118, 36),
      (568, 579, 118, 36),
      (706, 579, 118, 36),
  ]
  for slot in completed_slots:
    filters.append(drawbox(*slot, "0xffffff@0.76"))
    filters.append(drawbox(*slot, LINE, thickness=2))

  filters.append(
      drawtext(
          "1. queuectl stores waiting jobs as rows in PostgreSQL.",
          96,
          668,
          size=19,
          color=INK,
          font=TITLE_FONT,
          start=0.0,
          end=2.0,
      )
  )
  filters.append(
      drawtext(
          "2. Three workers lease the ready jobs and start processing.",
          96,
          668,
          size=19,
          color=INK,
          font=TITLE_FONT,
          start=2.0,
          end=4.4,
      )
  )
  filters.append(
      drawtext(
          "3. Most jobs complete directly, while one failed job waits in retry.",
          96,
          668,
          size=19,
          color=INK,
          font=TITLE_FONT,
          start=4.4,
          end=7.2,
      )
  )
  filters.append(
      drawtext(
          "4. The retried job runs again, fails again, and lands in the DLQ.",
          96,
          668,
          size=19,
          color=INK,
          font=TITLE_FONT,
          start=7.2,
          end=DURATION,
      )
  )

  default_fill = "0xe6f6f4@0.95"
  default_border = ACCENT
  emails_fill = "0xfff3db@0.96"
  emails_border = WARN
  done_fill = OK_SOFT
  done_border = OK

  waiting_jobs = [
      ("d-01", 160, 292, default_fill, default_border, ACCENT, 0.0, 2.0),
      ("d-02", 160, 350, default_fill, default_border, ACCENT, 0.0, 2.4),
      ("e-01", 460, 292, emails_fill, emails_border, WARN, 0.0, 2.2),
      ("e-02", 460, 350, emails_fill, emails_border, WARN, 0.0, 4.8),
  ]
  for label, x, y, fill, border, text_color, start, end in waiting_jobs:
    job_card(
        filters,
        x=x,
        y=y,
        label=label,
        fill=fill,
        border=border,
        text_color=text_color,
        start=start,
        end=end,
        w=142,
        h=34,
    )

  # Direct-success jobs.
  direct_success = [
      ("d-01", default_fill, default_border, ACCENT, (160, 292), (978, 240), (430, 580), (2.0, 2.6), (2.6, 3.8), (3.8, 4.4)),
      ("d-02", default_fill, default_border, ACCENT, (160, 350), (978, 360), (568, 580), (2.4, 3.0), (3.0, 4.2), (4.2, 4.8)),
      ("e-02", emails_fill, emails_border, WARN, (460, 350), (978, 240), (706, 580), (4.8, 5.4), (5.4, 6.4), (6.4, 7.0)),
  ]
  for label, fill, border, text_color, source, worker, done, move_in, work, move_out in direct_success:
    move_job_card(filters, label=label, fill=fill, border=border, text_color=text_color,
                  x1=source[0], y1=source[1], x2=worker[0], y2=worker[1],
                  start=move_in[0], end=move_in[1], w=142, h=34)
    job_card(filters, x=worker[0], y=worker[1], label=label, fill=fill, border=border,
             text_color=text_color, start=work[0], end=work[1], w=142, h=34)
    move_job_card(filters, label=label, fill=done_fill, border=done_border, text_color=OK,
                  x1=worker[0], y1=worker[1], x2=done[0], y2=done[1],
                  start=move_out[0], end=move_out[1], w=118, h=36)
    job_card(filters, x=done[0], y=done[1], label=label, fill=done_fill, border=done_border,
             text_color=OK, start=move_out[1], end=DURATION, w=118, h=36)

  # Retry then DLQ: e-01
  move_job_card(filters, label="e-01", fill=emails_fill, border=emails_border, text_color=WARN,
                x1=460, y1=292, x2=978, y2=300, start=2.2, end=2.8, w=142, h=34)
  job_card(filters, x=978, y=300, label="e-01", fill=emails_fill, border=emails_border,
           text_color=WARN, start=2.8, end=4.0, w=142, h=34)
  move_job_card(filters, label="e-01", fill=WARN_SOFT, border=emails_border, text_color=WARN,
                x1=978, y1=300, x2=742, y2=502, start=4.0, end=4.6, w=142, h=28)
  job_card(filters, x=742, y=502, label="e-01", fill=WARN_SOFT, border=emails_border,
           text_color=WARN, start=4.6, end=7.2, w=142, h=28)
  move_job_card(filters, label="e-01", fill=emails_fill, border=emails_border, text_color=WARN,
                x1=742, y1=502, x2=978, y2=300, start=7.2, end=7.8, w=142, h=34)
  job_card(filters, x=978, y=300, label="e-01", fill=emails_fill, border=emails_border,
           text_color=WARN, start=7.8, end=8.6, w=142, h=34)
  move_job_card(filters, label="e-01", fill=ERROR_SOFT, border=ERROR, text_color=ERROR,
                x1=978, y1=300, x2=997, y2=502, start=8.6, end=9.2, w=142, h=28)
  job_card(filters, x=997, y=502, label="e-01", fill=ERROR_SOFT, border=ERROR,
           text_color=ERROR, start=9.2, end=DURATION, w=142, h=28)

  return "[0:v]" + ",\n".join(filters) + "[v]"


def run_ffmpeg(command: list[str]) -> None:
  subprocess.run(command, check=True, cwd=ROOT)


def ensure_prerequisites() -> None:
  missing = [path for path in (TITLE_FONT, BODY_FONT, MONO_FONT) if not path.exists()]
  if missing:
    raise FileNotFoundError(f"Missing font files: {missing}")


def render() -> None:
  ensure_prerequisites()
  ASSETS_DIR.mkdir(parents=True, exist_ok=True)
  GRAPH_PATH.write_text(build_filtergraph(), encoding="utf-8")

  run_ffmpeg(
      [
          "ffmpeg",
          "-y",
          "-f",
          "lavfi",
          "-r",
          str(FPS),
          "-i",
          f"color=c={BG}:s={WIDTH}x{HEIGHT}:d={DURATION}",
          "-filter_complex_script",
          str(GRAPH_PATH),
          "-map",
          "[v]",
          "-c:v",
          "libx264",
          "-pix_fmt",
          "yuv420p",
          "-movflags",
          "+faststart",
          "-crf",
          "20",
          str(MP4_PATH),
      ]
  )

  run_ffmpeg(
      [
          "ffmpeg",
          "-y",
          "-i",
          str(MP4_PATH),
          "-vf",
          (
              "fps=12,scale=1100:-1:flags=lanczos,split[s0][s1];"
              "[s0]palettegen=stats_mode=diff:max_colors=128[p];"
              "[s1][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle"
          ),
          "-loop",
          "0",
          str(GIF_PATH),
      ]
  )

  run_ffmpeg(
      [
          "ffmpeg",
          "-y",
          "-ss",
          "7.9",
          "-i",
          str(MP4_PATH),
          "-frames:v",
          "1",
          "-update",
          "1",
          str(POSTER_PATH),
      ]
  )

  print(f"wrote {MP4_PATH}")
  print(f"wrote {GIF_PATH}")
  print(f"wrote {POSTER_PATH}")


if __name__ == "__main__":
  render()
