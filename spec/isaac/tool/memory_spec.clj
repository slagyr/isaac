(ns isaac.tool.memory-spec
  (:require
    [isaac.fs :as fs]
    [isaac.spec-helper :as helper]
    [isaac.tool.memory :as sut]
    [speclj.core :refer :all]))

(def test-dir "/test/memory")

(describe "Memory tools"

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "writes to today's UTC note"
    (binding [sut/*now* (java.time.Instant/parse "2026-04-21T10:00:00Z")]
      (sut/memory-write-tool {"content" "Hieronymus hates artichokes." "state_dir" test-dir})
      (should= "Hieronymus hates artichokes."
               (fs/slurp (str test-dir "/crew/main/memory/2026-04-21.md")))))

  (it "accepts a vector of entries"
    (binding [sut/*now* (java.time.Instant/parse "2026-04-21T10:00:00Z")]
      (sut/memory-write-tool {"content" ["Orpheus" "Grandma"] "state_dir" test-dir})
      (should= "Orpheus\nGrandma"
               (fs/slurp (str test-dir "/crew/main/memory/2026-04-21.md")))))

  (it "appends instead of overwriting"
    (binding [sut/*now* (java.time.Instant/parse "2026-04-21T10:00:00Z")]
      (sut/memory-write-tool {"content" "first" "state_dir" test-dir})
      (sut/memory-write-tool {"content" "second" "state_dir" test-dir})
      (should= "first\nsecond"
               (fs/slurp (str test-dir "/crew/main/memory/2026-04-21.md")))))

  (it "reads notes across an inclusive date range"
    (fs/mkdirs (str test-dir "/crew/main/memory"))
    (fs/spit (str test-dir "/crew/main/memory/2026-04-14.md") "The moonflowers bloomed last night.")
    (fs/spit (str test-dir "/crew/main/memory/2026-04-16.md") "Wind knocked over the hedgehog figurine.")
    (fs/spit (str test-dir "/crew/main/memory/2026-04-19.md") "User found a geode in the attic.")
    (let [result (sut/memory-get-tool {"start_time" "2026-04-14" "end_time" "2026-04-16" "state_dir" test-dir})]
      (should-contain "The moonflowers bloomed last night." (:result result))
      (should-contain "Wind knocked over the hedgehog figurine." (:result result))
      (should-not-contain "User found a geode in the attic." (:result result))))

  (it "searches all memory files and returns ripgrep-style lines"
    (fs/mkdirs (str test-dir "/crew/main/memory"))
    (fs/spit (str test-dir "/crew/main/memory/2026-04-15.md") "Orpheus brought a dead mouse to the back door.")
    (fs/spit (str test-dir "/crew/main/memory/2026-04-19.md") "Orpheus sulked under the porch for most of the afternoon.")
    (fs/spit (str test-dir "/crew/main/memory/2026-04-20.md") "The moonflowers bloomed last night.")
    (let [result (sut/memory-search-tool {"query" "Orpheus" "state_dir" test-dir})]
      (should-contain "2026-04-15.md:1:Orpheus brought a dead mouse to the back door." (:result result))
      (should-contain "2026-04-19.md:1:Orpheus sulked under the porch for most of the afternoon." (:result result))
      (should-not-contain "moonflowers" (:result result))))

  (it "uses the session crew when provided"
    (helper/create-session! test-dir "crew-session" {:crew "marvin" :agent "marvin" :cwd test-dir})
    (binding [sut/*now* (java.time.Instant/parse "2026-04-21T10:00:00Z")]
      (sut/memory-write-tool {"content" "tea note" "session_key" "crew-session" "state_dir" test-dir})
      (should= "tea note"
               (fs/slurp (str test-dir "/crew/marvin/memory/2026-04-21.md"))))))
