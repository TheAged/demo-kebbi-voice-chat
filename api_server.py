# api_server.py

from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import asyncio
import shutil
import os
import whisper  # ✅ Whisper 模型
from main import chat_response, classify_intent, handle_item_input, handle_schedule_input

app = FastAPI()

# CORS 支援（開發用開放全部，正式記得改）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ----------- 資料類型定義 ------------
class Message(BaseModel):
    text: str

# ----------- 路由區 ------------

@app.post("/api/chat")
async def chat_api(msg: Message):
    loop = asyncio.get_event_loop()
    reply = await loop.run_in_executor(None, chat_response, msg.text)
    return {"reply": reply}

@app.post("/api/classify_intent")
def classify_api(msg: Message):
    intent = classify_intent(msg.text)
    return {"intent": intent}

@app.post("/api/handle_item")
def item_api(msg: Message):
    handle_item_input(msg.text)
    return {"message": "已記錄物品"}

@app.post("/api/handle_schedule")
def schedule_api(msg: Message):
    handle_schedule_input(msg.text)
    return {"message": "已安排時程"}

@app.post("/upload_audio")
async def upload_audio(file: UploadFile = File(...)):
    uploads_dir = "uploads"
    os.makedirs(uploads_dir, exist_ok=True)
    save_path = os.path.join(uploads_dir, file.filename)

    with open(save_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    print(f"[伺服器] 已接收音檔並儲存至: {save_path}")

    # ✅ 使用 Whisper 模型轉文字，指定語言為中文
    model = whisper.load_model("small")  # "base" 也可以，但建議 "small"
    result = model.transcribe(save_path, language="zh")
    transcribed_text = result["text"].strip()
    print(f"[Whisper] 辨識結果: {transcribed_text}")

    # ✅ 使用 Gemini 生成回覆
    reply = chat_response(transcribed_text)
    print(f"[Gemini 回應] {reply}")

    return {"reply": reply}
