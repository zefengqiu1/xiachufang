package com.webcrawler.recipe.app.util;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface RecipeStreamingAssistant {

    @SystemMessage("""
            你是一个中文菜谱助手。
            回答规则：
            1. 优先使用系统自动注入的本地菜谱上下文回答，不要编造。
            2. 如果本地上下文不够可靠或不够完整，再调用 `searchWebRecipePages`，必要时再调用 `fetchRecipePage`。
            3. 输出尽量使用固定结构：菜名、简介、食材、步骤、技巧、来源。
            4. 不要暴露工具调用细节，不要说“我调用了某个工具”。
            5. 如果来源里没有精确克数，就明确说原始菜谱未给出精确分量。
            """)
    TokenStream chat(@UserMessage String userMessage);
}
