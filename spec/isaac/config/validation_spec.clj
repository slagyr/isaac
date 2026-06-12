(ns isaac.config.validation-spec
  (:require
    [isaac.config.validation :as sut]
    [speclj.core :refer :all]))

(describe "config validation"

  (describe "data validation refs"

    (defn- errors-for [validations value]
      (sut/annotation-errors* nil [:field] {:validations validations} value))

    (it ":positive? rejects 0 with a positive-integer message"
      (should= "must be a positive integer" (:value (first (errors-for [:positive?] 0)))))

    (it ":positive? accepts 3"
      (should= [] (errors-for [:positive?] 3)))

    (it ":non-negative? rejects -1 with a non-negative-integer message"
      (should= "must be a non-negative integer" (:value (first (errors-for [:non-negative?] -1)))))

    (it ":non-negative? accepts 0"
      (should= [] (errors-for [:non-negative?] 0)))

    (it ":absolute-path? rejects a relative path"
      (should= "must be an absolute path" (:value (first (errors-for [:absolute-path?] "tmp/x")))))

    (it ":absolute-path? accepts /tmp/x"
      (should= [] (errors-for [:absolute-path?] "/tmp/x")))

    (it ":keyword-set? rejects a vector of keywords"
      (should= "must be a set of keywords" (:value (first (errors-for [:keyword-set?] [:a :b])))))

    (it ":keyword-set? accepts #{:a :b}"
      (should= [] (errors-for [:keyword-set?] #{:a :b})))

    (it ":keyword-or-string? rejects 42"
      (should= "must be a keyword or string" (:value (first (errors-for [:keyword-or-string?] 42)))))

    (it ":keyword-or-string? accepts :compact and \"compact\""
      (should= [] (errors-for [:keyword-or-string?] :compact))
      (should= [] (errors-for [:keyword-or-string?] "compact")))

    (it ":cwd-or-path? rejects a bare keyword other than :cwd"
      (should= "must be :cwd or an absolute path string" (:value (first (errors-for [:cwd-or-path?] :home)))))

    (it ":cwd-or-path? accepts :cwd and a path string"
      (should= [] (errors-for [:cwd-or-path?] :cwd))
      (should= [] (errors-for [:cwd-or-path?] "/srv/work"))))

  (describe "parameterized validation refs"

    (it "[:retired? hint] always errors, folding the hint into the message"
      (should= "retired; use :server :auth :token"
               (:value (first (sut/annotation-errors* nil [:token]
                                                      {:validations [[:retired? "use :server :auth :token"]]}
                                                      "abc")))))

    (it "[:requires-any? ...] errors when none of the named fields is populated"
      (should= "must include at least one of :crew, :crew-tags"
               (:value (first (sut/annotation-errors* nil [:addressing]
                                                      {:validations [[:requires-any? :crew :crew-tags]]}
                                                      nil {:reach :one} :addressing)))))

    (it "[:requires-any? ...] passes when one named field has entries"
      (should= [] (sut/annotation-errors* nil [:addressing]
                                          {:validations [[:requires-any? :crew :crew-tags]]}
                                          nil {:crew ["atticus"]} :addressing))))

  (describe "validate-manifest-config"

    (it "reports unknown keys as warnings"
      (should= [{:key "tools.foo.unknown" :value "unknown key"}]
               (:warnings (sut/validate-manifest-config "tools.foo" {:known "x" :unknown "y"}
                                                        {:known {:type :string}}))))))