;; mutation-tested: pending
(ns isaac.tool.skill-spec
  (:require
    [isaac.fs :as fs]
    [isaac.nexus :as nexus]
    [isaac.spec-helper :as helper]
    [isaac.tool.skill :as sut]
    [speclj.core :refer :all]))

(describe "skill tools"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (nexus/-with-nested-nexus {:state-dir "/test/isaac" :fs (fs/mem-fs)}
      (helper/with-memory-store
        (example))))

  (defn- write-skill! [path content]
    (let [fs* (nexus/get :fs)]
      (fs/mkdirs fs* (fs/parent path))
      (fs/spit fs* path content)))

  (it "loads a discovered skill body for the calling session"
    (helper/create-session! "/test/isaac" "work-sess" {:crew "main" :cwd "/workspace/project"})
    (write-skill! "/test/isaac/config/skills/greenhouse-protocol/SKILL.md"
                  (str "---\n"
                       "type: skill\n"
                       "description: Use when tending specimens\n"
                       "---\n\n"
                       "Always quarantine new specimens for one cycle."))
    (should= {:result "Always quarantine new specimens for one cycle."}
             (sut/load-skill-tool {"session_key" "work-sess"
                                   "name"        "greenhouse-protocol"})))

  (it "lists discovered skills in stable sorted order"
    (helper/create-session! "/test/isaac" "work-sess" {:crew "main" :cwd "/workspace/project"})
    (write-skill! "/test/isaac/config/skills/greenhouse-protocol/SKILL.md"
                  (str "---\n"
                       "type: skill\n"
                       "description: Use when tending specimens\n"
                       "---\n\n"
                       "Always quarantine new specimens for one cycle."))
    (write-skill! "/test/isaac/config/skills/aeroponics/SKILL.md"
                  (str "---\n"
                       "type: skill\n"
                       "description: Use for soil-free growing\n"
                       "---\n\n"
                       "Mist the roots on a schedule."))
    (should= {:result (str "Available skills:\n"
                           "- aeroponics: Use for soil-free growing\n"
                           "- greenhouse-protocol: Use when tending specimens\n\n"
                           "Use load_skill to load a skill body on demand.")}
             (sut/list-skills-tool {"session_key" "work-sess"})))

  (it "errors when the requested skill does not exist"
    (helper/create-session! "/test/isaac" "work-sess" {:crew "main" :cwd "/workspace/project"})
    (let [result (sut/load-skill-tool {"session_key" "work-sess"
                                       "name"        "missing"})]
      (should (:isError result))
      (should= "unknown skill: missing" (:error result))))

  (it "errors when a resource is requested before resource loading ships"
    (helper/create-session! "/test/isaac" "work-sess" {:crew "main" :cwd "/workspace/project"})
    (let [result (sut/load-skill-tool {"session_key" "work-sess"
                                       "name"        "greenhouse-protocol"
                                       "resource"    "checklist.md"})]
      (should (:isError result))
      (should= "skill resources are not supported yet" (:error result)))))
