(ns mdm.isaac.thought.dedupe-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.thought.schema :as schema]
            [mdm.isaac.thought.dedupe :as sut]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 384 0.1)))

(describe "Thought Deduplication"

  (helper/with-schemas [schema/thought])

  (context "find-exact-match"

    (it "returns nil when no match exists"
      (should-be-nil (sut/find-exact-match "unique thought")))

    (it "returns matching thought when exact content exists"
      (let [existing (db/tx {:kind :thought :content "hello world" :embedding test-embedding})]
        (should= (:id existing) (:id (sut/find-exact-match "hello world")))))

    (it "matches case-insensitively"
      (let [existing (db/tx {:kind :thought :content "Hello World" :embedding test-embedding})]
        (should= (:id existing) (:id (sut/find-exact-match "hello world")))))

    )

  (context "increment-seen-count!"

    (it "increments seen-count from nil to 2"
      (let [thought (db/tx {:kind :thought :content "test" :embedding test-embedding})
            updated (sut/increment-seen-count! thought)]
        (should= 2 (:seen-count updated))))

    (it "increments existing seen-count"
      (let [thought (db/tx {:kind :thought :content "test" :embedding test-embedding :seen-count 5})
            updated (sut/increment-seen-count! thought)]
        (should= 6 (:seen-count updated))))

    (it "persists the updated count"
      (let [thought (db/tx {:kind :thought :content "test" :embedding test-embedding :seen-count 3})
            _       (sut/increment-seen-count! thought)
            fetched (db/entity :thought (:id thought))]
        (should= 4 (:seen-count fetched))))

    )

  (context "find-similar-candidates"

    (it "returns empty list when no thoughts exist"
      (should= [] (sut/find-similar-candidates test-embedding 5)))

    (it "returns similar thoughts limited by max-candidates"
      (db/tx {:kind :thought :content "one" :embedding test-embedding})
      (db/tx {:kind :thought :content "two" :embedding test-embedding})
      (db/tx {:kind :thought :content "three" :embedding test-embedding})
      (let [candidates (sut/find-similar-candidates test-embedding 2)]
        (should= 2 (count candidates))))

    (it "orders by similarity (most similar first)"
      (let [query-emb  (vec (repeat 384 0.5))
            similar    (db/tx {:kind :thought :content "similar" :embedding (vec (repeat 384 0.5))})
            _different (db/tx {:kind :thought :content "different" :embedding (vec (repeat 384 0.0))})
            candidates (sut/find-similar-candidates query-emb 1)]
        (should= (:id similar) (:id (first candidates)))))

    )

  (context "find-semantic-match"

    (it "returns nil when no candidates exist"
      (let [llm-fn (fn [_] "NO")]
        (should-be-nil (sut/find-semantic-match "new thought" [] llm-fn))))

    (it "returns nil when LLM says NO to all candidates"
      (let [candidates [{:content "candidate one"} {:content "candidate two"}]
            llm-fn (fn [_] "NO")]
        (should-be-nil (sut/find-semantic-match "new thought" candidates llm-fn))))

    (it "returns first matching candidate when LLM says YES"
      (let [candidates [{:id 1 :content "not a match"} {:id 2 :content "semantic match"}]
            llm-fn (fn [prompt]
                     (if (clojure.string/includes? prompt "semantic match")
                       "YES"
                       "NO"))]
        (should= 2 (:id (sut/find-semantic-match "new thought" candidates llm-fn)))))

    (it "stops checking after first match"
      (let [call-count (atom 0)
            candidates [{:id 1 :content "first"} {:id 2 :content "second"}]
            llm-fn (fn [_] (swap! call-count inc) "YES")]
        (sut/find-semantic-match "new thought" candidates llm-fn)
        (should= 1 @call-count)))

    )

  (context "save-if-unique!"

    (it "increments seen-count and returns nil on exact match"
      (let [existing (db/tx {:kind :thought :content "duplicate" :embedding test-embedding :seen-count 1})
            new-thought {:kind :thought :content "duplicate" :embedding test-embedding}
            llm-fn (fn [_] "NO")
            result (sut/save-if-unique! new-thought llm-fn 5)]
        (should-be-nil result)
        (should= 2 (:seen-count (db/entity :thought (:id existing))))))

    (it "increments seen-count and returns nil on semantic match"
      (let [existing (db/tx {:kind :thought :content "I love cats" :embedding test-embedding :seen-count 1})
            new-thought {:kind :thought :content "Cats are great" :embedding test-embedding}
            llm-fn (fn [_] "YES")
            result (sut/save-if-unique! new-thought llm-fn 5)]
        (should-be-nil result)
        (should= 2 (:seen-count (db/entity :thought (:id existing))))))

    (it "saves unique thought with seen-count 1"
      (let [new-thought {:kind :thought :content "brand new" :embedding test-embedding}
            llm-fn (fn [_] "NO")
            result (sut/save-if-unique! new-thought llm-fn 5)]
        (should-not-be-nil result)
        (should= 1 (:seen-count result))
        (should= "brand new" (:content result))))

    (it "respects max-candidates parameter"
      (db/tx {:kind :thought :content "one" :embedding test-embedding})
      (db/tx {:kind :thought :content "two" :embedding test-embedding})
      (db/tx {:kind :thought :content "three" :embedding test-embedding})
      (let [call-count (atom 0)
            new-thought {:kind :thought :content "unique" :embedding (vec (repeat 384 0.9))}
            llm-fn (fn [_] (swap! call-count inc) "NO")
            _result (sut/save-if-unique! new-thought llm-fn 2)]
        (should= 2 @call-count)))

    )

  )
