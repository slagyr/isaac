(ns mdm.isaac.user.google-spec
  (:require [c3kit.apron.utilc :as utilc]
            [mdm.isaac.user.google]
            [mdm.isaac.user.core :as user]
            [c3kit.wire.rest :as rest]
            [speclj.core :refer :all])
  (:import (clojure.lang ExceptionInfo)))


(def pkey-1
  {:kty "RSA",
   :kid "UaIIFY2fW4",
   :use "sig",
   :alg "RS256",
   :n   "sxzLtSWjplO4nMymVwkknn6WrQvK4sz7F1rrIwOKPa3SpltaB719cfxFfoE4UqHfVxHXsoYew82ViYz5whp0CuqgWi2t4HYpSTCQdVCNIXpsMxA8QqTfIlc-EUFNuUMziY-hJXqi4i-woI0HiwPEkO-AhWy86L9-J_1I1yw22-BICacAU7J9UTBBwHu0wkRHiyPe4pHow1wa91v5OM09XHqjHpiFrJD7bOBl6Y3EuBXEWy3VEA-S2IchqVGmvNGNZo6J9WtSHEcL6ussFWPJoIo2GR4BrgHvZGUvhgbHrKjCPrIAhliH0er3pF5_0UTSqW0Xg_Q2iQpxo9TRn-kHpw",
   :e   "AQAB"})

(def pkey-2
  (assoc pkey-1
    :kid "Sf2lFqwkpX"
    :n "oNe3ZKHU5-fnmbjhCamUpBSyLkR4jbQy-PCZU4cr7tyPcFokyZ1CjSGm44sw3EPONWO6bWgKZYBX2UPv7UM3GBIuB8qBkkN0_vu0Kdr8KUWJ-6m9fnKgceDil4K4TsSS8Owe9qnP9XjjmVRK7cCEjew4GYqQ7gRcHUjIQ-PrKkNBOOijxLlwckeQK2IN9WS_CBXVMleXLutfYAHpwr2KoAmt5BQvPFqBegozHaTc2UvarcUPKMrl-sjY_AXobH7NjqfbBLRJLzS2EzE4y865QiBpwwdhlK4ZQ3g1DCV57BDKvoBX0guCDNSFvoPuIjMmTxZEUbwrJ1CQ4Ib5j4VCkQ"))

(def pkeys {:keys [pkey-1 pkey-2]})

(def url "https://www.googleapis.com/oauth2/v3/certs")

(describe "Google Auth"

  (with-stubs)

  (context "public keys"

    (it "can't be fetched"
      (with-redefs [rest/get! (stub :rest/get! {:invoke (fn [& args] (throw (RuntimeException. "foo")))})]
        (should-throw ExceptionInfo (format "Could not fetch pkeys from %s" url)
                      (user/pkeys :google))))

    (it "are fetched"
      (with-redefs [rest/get! (stub :rest/get! {:return {:body (utilc/->json pkeys)}})]
        (let [pkeys (user/pkeys :google)]
          (should= pkey-1 (pkeys "UaIIFY2fW4")))))
    )
  )
