export type Role = "user" | "assistant";

export type ChatMessage = {
  id: string;
  role: Role;
  text: string;
};

export type ChatStreamEvent =
  | {
      type: "start";
      sessionId: string;
    }
  | {
      type: "chunk";
      content: string;
    }
  | {
      type: "end";
      usedTools: string[];
      metadata: Record<string, unknown>;
    };
