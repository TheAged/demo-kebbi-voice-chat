import json
import re
import emoji
from datetime import datetime
import google.generativeai as genai
import edge_tts  # 用於語音播放

# 初始化 Gemini Flash 模型
genai.configure(api_key="金鑰")
model = genai.GenerativeModel("gemini-2.0-flash")

ITEMS_FILE = "items.json"
SCHEDULE_FILE = "schedules.json"
CHAT_HISTORY_FILE = "chat_history.json"

# ─────── 工具函式 ───────
def clean_text_from_stt(text):
    text = emoji.replace_emoji(text, replace="")
    text = re.sub(r"[^\w\s\u4e00-\u9fff.,!?！？。]", "", text)
    return text.strip()

def safe_generate(prompt):
    try:
        return model.generate_content(prompt).text.strip()
    except Exception as e:
        print("Gemini API 錯誤：", e)
        return None

def load_json(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except:
        return []

def save_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def save_chat_log(user_input, ai_response):
    log = {
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "user": user_input,
        "response": ai_response
    }
    records = load_json(CHAT_HISTORY_FILE)
    records.append(log)
    save_json(CHAT_HISTORY_FILE, records)

# ─────── 語意分類 ───────
def classify_intent(text):
    prompt = f"""
請根據句子判斷使用者想做什麼動作，只回傳以下其中一項（不得自由發揮）：
- 記錄物品
- 安排時程
- 聊天
句子：「{text}」
"""
    return safe_generate(prompt)

# ─────── 聊天功能 ───────
def detect_emotion(text):
    prompt = f"""
你是一個情緒分析助手，請從以下句子中判斷使用者的情緒，並只回覆「快樂」、「悲傷」、「生氣」或「中性」其中一種。
句子：「{text}」
"""
    emotion = safe_generate(prompt)
    if emotion not in ["快樂", "悲傷", "生氣", "中性"]:
        return "中性"
    return emotion

def generate_prompt_with_context(text):
    history = load_json(CHAT_HISTORY_FILE)[-3:]
    context = "\n".join([f"使用者：{h['user']}\nAI：{h['response']}" for h in history])
    tone = {
        "快樂": "用開朗活潑的語氣",
        "悲傷": "用溫柔安慰的語氣",
        "生氣": "用穩定理性的語氣",
        "中性": "自然地"
    }[detect_emotion(text)]

    return f"""{context}
使用者：{text}
你是一個親切自然、會說口語中文的朋友型機器人，請根據上面的對話與語氣，給出一段自然的中文回應。
請避免列點、格式化、過於正式的用詞，不要教學語氣，也不要問太多問題，只需回一句自然的回答即可。
請以{tone}語氣回應，直接說中文："""

def chat_response(text):
    prompt = generate_prompt_with_context(text)
    reply = safe_generate(prompt)
    if reply:
        save_chat_log(text, reply)
    return reply

# ─────── 記錄物品 ───────
def handle_item_input(text):
    prompt = f"""請從下面這句話中擷取出下列資訊，用 JSON 格式回覆：
- item：物品名稱
- location：放置位置
- owner：誰的（如果沒提到就填「我」）
句子：「{text}」"""

    reply = safe_generate(prompt)
    if not reply:
        return

    try:
        data = json.loads(reply.strip("`").replace("json", "").strip())
        data["timestamp"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        records = load_json(ITEMS_FILE)
        records.append(data)
        save_json(ITEMS_FILE, records)
    except:
        print(f"無法解析回應：{reply}")

# ─────── 安排時程 ───────
def handle_schedule_input(text):
    prompt = f"""請從下列句子中擷取資訊並以 JSON 格式回覆（key：task, location, place, time, person）：
句子：「{text}」"""

    reply = safe_generate(prompt)
    if not reply:
        return

    try:
        data = json.loads(reply.strip("`").replace("json", "").strip())
        data["timestamp"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        schedules = load_json(SCHEDULE_FILE)
        schedules.append(data)
        save_json(SCHEDULE_FILE, schedules)
    except:
        print(f"無法解析排程回應：{reply}")
