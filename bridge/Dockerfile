FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY chatgpt_broker.py /app/chatgpt_broker.py
RUN useradd --system --uid 10101 --create-home --home-dir /nonexistent study
USER 10101
EXPOSE 8765
CMD ["python3", "-B", "chatgpt_broker.py"]
