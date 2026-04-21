(ns isaac.config.cli.common-spec
  (:require
    [isaac.config.cli.common :as sut]
    [speclj.core :refer :all]))

(describe "config cli common"

  (describe "normalize-path"

    (it "preserves dotted paths without a leading slash"
      (should= "crew.marvin.soul" (sut/normalize-path "crew.marvin.soul")))

    (it "splits on '/' when the path starts with '/'"
      (should= "crew.marvin.soul" (sut/normalize-path "/crew/marvin/soul")))

    (it "escapes segments with dots as bracket-strings in slash-mode"
      (should= "crew[\"john.doe\"].model" (sut/normalize-path "/crew/john.doe/model")))

    (it "escapes segments with spaces as bracket-strings in slash-mode"
      (should= "crew[\"my crew\"].soul" (sut/normalize-path "/crew/my crew/soul")))))
