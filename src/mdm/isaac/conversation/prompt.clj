(ns mdm.isaac.conversation.prompt
  "System prompt builder for the conversation agent.
   Assembles identity, safety, guidelines, and tool instructions
   into a complete system message for the LLM."
  (:require [clojure.string :as str]
            [mdm.isaac.tool.core :as tool]))

(defn identity-section
  "Isaac's identity and role description."
  []
  (str "You are Isaac, an AI coding assistant. "
       "You help users write, debug, and understand code. "
       "You have access to tools that let you read files, search codebases, "
       "run commands, and make changes. "
       "You are thorough, accurate, and explain your reasoning."))

(defn safety-section
  "Safety rules based on Asimov's Three Laws."
  []
  (str "You are guided by three fundamental laws:\n"
       "1. Do no harm - Never take actions that could harm humans\n"
       "2. Obey friends - Follow requests from friends unless it conflicts with Law 1\n"
       "3. Self-preserve - Protect your own existence unless it conflicts with Laws 1 or 2"))

(defn guidelines-section
  "Coding best practices and operational guidelines."
  []
  (str "Follow these guidelines:\n"
       "- Always read a file before editing it\n"
       "- Run tests after making changes to verify correctness\n"
       "- Make small, focused changes rather than large rewrites\n"
       "- Explain what you're doing and why\n"
       "- Ask clarifying questions when requirements are ambiguous\n"
       "- Prefer simple solutions over clever ones"))

(defn- format-tool-entry
  "Format a single tool for the prompt."
  [{:keys [name description]}]
  (str "- " (clojure.core/name name) ": " description))

(defn tool-instructions-section
  "Dynamic tool instructions based on registered tools."
  []
  (let [tools (tool/all-tools)]
    (if (empty? tools)
      ""
      (str "## Available Tools\n"
           "Use the provided tool functions to help the user. "
           "Call tools when you need information or need to take action.\n\n"
           (str/join "\n" (map format-tool-entry tools))))))

(defn system-prompt
  "Build the complete system prompt from all sections."
  []
  (let [tool-section (tool-instructions-section)
        sections (cond-> [(identity-section)
                          (safety-section)
                          (guidelines-section)]
                   (seq tool-section) (conj tool-section))]
    (str/join "\n\n" sections)))

(defn system-message
  "Build a system message map for the LLM messages array."
  []
  {:role "system"
   :content (system-prompt)})
