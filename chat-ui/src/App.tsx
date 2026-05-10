import { FormEvent, useState } from "react";
import { streamChatMessage } from "./api";
import type { ChatMessage } from "./types";

const starterPrompts = [
  "宫保鸡丁怎么做？",
  "鱼香肉丝怎么做？",
  "麻婆豆腐怎么做？",
];

function createMessage(role: ChatMessage["role"], text: string): ChatMessage {
  return {
    id: `${role}-${crypto.randomUUID()}`,
    role,
    text,
  };
}

export default function App() {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [input, setInput] = useState("宫保鸡丁怎么做？");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([
    createMessage(
      "assistant",
      "直接输入菜名和问题，例如“宫保鸡丁怎么做？”。系统会优先返回本地菜谱库里的结果。",
    ),
  ]);

  async function submitQuestion(event?: FormEvent<HTMLFormElement>, preset?: string) {
    event?.preventDefault();
    const question = (preset ?? input).trim();
    if (!question || loading) {
      return;
    }

    setError(null);
    setLoading(true);
    const assistantId = `assistant-${crypto.randomUUID()}`;
    setMessages((current) => [
      ...current,
      createMessage("user", question),
      { id: assistantId, role: "assistant", text: "" },
    ]);
    if (!preset) {
      setInput("");
    }

    try {
      await streamChatMessage({
        sessionId,
        message: question,
      }, (event) => {
        if (event.type === "start") {
          setSessionId(event.sessionId);
          return;
        }

        if (event.type === "chunk") {
          setMessages((current) =>
            current.map((message) =>
              message.id === assistantId
                ? { ...message, text: message.text + event.content }
                : message,
            ),
          );
          return;
        }

        if (event.type === "end") {
          setLoading(false);
        }
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "请求失败";
      setError(message);
      setMessages((current) => [
        ...current.filter((item) => item.id !== assistantId),
        createMessage("assistant", "后端请求失败，请确认 `http://localhost:8091/api/chat/stream` 可用。"),
      ]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="recipe-app">
      <section className="recipe-shell">
        <div className="recipe-hero">
          <p className="recipe-kicker">Xiachufang Recipe Demo</p>
          <h1>问一句话，直接返回菜谱</h1>
          <p className="recipe-copy">
            面向客户的最小交互版。输入“宫保鸡丁怎么做？”这类问题，前端直接调用后端聊天接口并展示返回结果。
          </p>
          <div className="recipe-prompts">
            {starterPrompts.map((prompt) => (
              <button key={prompt} type="button" className="prompt-button" onClick={() => void submitQuestion(undefined, prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        </div>

        <section className="chat-panel">
          <div className="chat-header">
            <div>
              <p className="chat-label">菜谱问答</p>
              <h2>本地知识库优先</h2>
            </div>
            <span className="chat-status">{loading ? "正在返回..." : "后端地址：/api/chat/stream"}</span>
          </div>

          <div className="message-list">
            {messages.map((message) => (
              <article key={message.id} className={`message-card ${message.role}`}>
                <span className="message-role">{message.role === "user" ? "你" : "菜谱助手"}</span>
                <pre>{message.text}</pre>
              </article>
            ))}
          </div>

          <form className="composer" onSubmit={(event) => void submitQuestion(event)}>
            <textarea
              rows={4}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="例如：宫保鸡丁怎么做？"
            />
            <div className="composer-footer">
              <span className="composer-hint">建议直接输入菜名 + “怎么做”</span>
              <button type="submit" disabled={loading || !input.trim()}>
                {loading ? "查询中..." : "发送"}
              </button>
            </div>
          </form>

          {error ? <p className="error-text">{error}</p> : null}
        </section>
      </section>
    </main>
  );
}
