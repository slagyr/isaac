(ns isaac.prompt.builder
  "Generic system prompt builder for Isaac.
   Assembles identity, safety, guidelines, and tool instructions
   into a complete system prompt suitable for any LLM backend."
  (:require [clojure.string :as str]))

(defn identity-section
  "Isaac's identity and role description."
  []
  (str "You are Isaac, an AI assistant. "
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
  "Format a single tool map for inclusion in the prompt."
  [{:keys [name description]}]
  (str "- " (clojure.core/name name) ": " description))

(defn tool-instructions-section
  "Build tool instructions from a sequence of tool maps.
   Each tool map should have :name and :description keys.
   Returns empty string when tools is nil or empty."
  [tools]
  (if (empty? tools)
    ""
    (str "## Available Tools\n"
         "Use the provided tool functions to help the user. "
         "Call tools when you need information or need to take action.\n\n"
         (str/join "\n" (map format-tool-entry tools)))))

(defn system-prompt
  "Build the complete system prompt from all sections.
   Accepts an optional sequence of tool maps."
  ([] (system-prompt nil))
  ([tools]
   (let [tool-section (tool-instructions-section tools)
         sections (cond-> [(identity-section)
                           (safety-section)
                           (guidelines-section)]
                    (seq tool-section) (conj tool-section))]
     (str/join "\n\n" sections))))
