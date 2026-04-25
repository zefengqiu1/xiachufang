import type { ChatResponse, ChatStreamEvent } from "./types";

const API_BASE = "/api";

export async function sendChatMessage(payload: {
  sessionId: string | null;
  message: string;
}): Promise<ChatResponse> {
  const response = await fetch(`${API_BASE}/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Chat request failed: ${response.status}`);
  }

  return response.json();
}

export async function streamChatMessage(
  payload: {
    sessionId: string | null;
    message: string;
  },
  onEvent: (event: ChatStreamEvent) => void,
): Promise<void> {
  const response = await fetch(`${API_BASE}/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok || !response.body) {
    throw new Error(`Chat stream request failed: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });

    let newlineIndex = buffer.indexOf("\n");
    while (newlineIndex >= 0) {
      const line = buffer.slice(0, newlineIndex).trim();
      buffer = buffer.slice(newlineIndex + 1);
      if (line) {
        onEvent(JSON.parse(line) as ChatStreamEvent);
      }
      newlineIndex = buffer.indexOf("\n");
    }

    if (done) {
      const tail = buffer.trim();
      if (tail) {
        onEvent(JSON.parse(tail) as ChatStreamEvent);
      }
      return;
    }
  }
}
