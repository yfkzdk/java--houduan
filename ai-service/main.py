"""
FastAPI wrapper — 把 agentic_chatbot_hitl_backend.py 的 LangGraph 智能体
暴露成 REST + SSE 接口，供 Java 业务层调用。

启动:  cd ai-service
       pip install -r requirements.txt
       copy .env.example .env   (填 API Key)
       uvicorn main:app --host 0.0.0.0 --port 8000 --reload
"""

import json
import sys
import os
import tempfile

from fastapi import FastAPI, HTTPException, UploadFile, File
from fastapi.responses import StreamingResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# ── 导入 LangGraph 引擎（同目录） ──
from agentic_chatbot_hitl_backend import (
    chatbot,
    get_all_threads,
    ingest_rag_document,
)
from langchain_core.messages import HumanMessage, AIMessage, ToolMessage
from langgraph.types import Command

# ── FastAPI app ──
app = FastAPI(
    title="Agentic Chatbot AI Service",
    version="2.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_headers=["*"],
    allow_methods=["*"],
)


# ── 请求体 ──
class ChatRequest(BaseModel):
    thread_id: str
    message: str


class ResumeRequest(BaseModel):
    thread_id: str
    decision: str  # "yes" = 批准, 其他 = 拒绝


# ── 工具函数 ──
def _get_pending_interrupt(thread_id: str):
    """查 LangGraph 是否有待处理的 HITL 中断"""
    config = {"configurable": {"thread_id": thread_id}}
    try:
        state = chatbot.get_state(config)
        direct = getattr(state, "interrupts", ()) or ()
        if direct:
            return direct[0]
        for task in getattr(state, "tasks", ()) or ():
            interrupts = getattr(task, "interrupts", ()) or ()
            if interrupts:
                return interrupts[0]
    except Exception:
        pass
    return None


# ── SSE 事件序列化 ──
def _sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


# ── 端点 ──

@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/threads")
async def list_threads():
    return {"threads": get_all_threads()}


@app.post("/chat/stream")
async def chat_stream(req: ChatRequest):
    """SSE 流式对话 — Java ChatController 核心对接点"""

    async def generate():
        config = {
            "configurable": {"thread_id": req.thread_id},
            "metadata": {"thread_id": req.thread_id},
            "run_name": "chat_trace",
        }
        try:
            for msg_chunk, _metadata in chatbot.stream(
                {"messages": [HumanMessage(content=req.message)]},
                config=config,
                stream_mode="messages",
            ):
                if isinstance(msg_chunk, AIMessage) and msg_chunk.content:
                    yield _sse({"type": "text", "content": msg_chunk.content})
                elif isinstance(msg_chunk, ToolMessage):
                    yield _sse({"type": "tool", "name": getattr(msg_chunk, "name", "tool")})

            # 检查 HITL
            pending = _get_pending_interrupt(req.thread_id)
            if pending:
                yield _sse({"type": "hitl", "prompt": str(pending.value)})

            yield "data: [DONE]\n\n"

        except Exception as e:
            yield _sse({"type": "error", "content": str(e)})
            yield "data: [DONE]\n\n"

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/chat/resume")
async def chat_resume(req: ResumeRequest):
    """HITL 审批恢复"""

    async def generate():
        config = {
            "configurable": {"thread_id": req.thread_id},
            "metadata": {"thread_id": req.thread_id},
            "run_name": "hitl_resume_trace",
        }
        try:
            for msg_chunk, _metadata in chatbot.stream(
                Command(resume=req.decision),
                config=config,
                stream_mode="messages",
            ):
                if isinstance(msg_chunk, AIMessage) and msg_chunk.content:
                    yield _sse({"type": "text", "content": msg_chunk.content})
                elif isinstance(msg_chunk, ToolMessage):
                    yield _sse({"type": "tool", "name": getattr(msg_chunk, "name", "tool")})

            pending = _get_pending_interrupt(req.thread_id)
            if pending:
                yield _sse({"type": "hitl", "prompt": str(pending.value)})

            yield "data: [DONE]\n\n"

        except Exception as e:
            yield _sse({"type": "error", "content": str(e)})
            yield "data: [DONE]\n\n"

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/ingest")
async def ingest_pdf(file: UploadFile = File(...)):
    """PDF 上传 → FAISS 向量化"""
    if not file.filename or not file.filename.lower().endswith(".pdf"):
        raise HTTPException(400, "Only PDF files are supported")

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name
        ingest_rag_document(tmp_path)
        os.remove(tmp_path)
        return {"status": "ok", "filename": file.filename}
    except Exception as e:
        raise HTTPException(500, str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
