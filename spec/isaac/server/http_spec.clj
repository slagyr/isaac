(ns isaac.server.http-spec
  (:require
    [isaac.logger :as log]
    [isaac.server.http :as sut]
    [speclj.core :refer :all]))

(describe "HTTP handler"

  (it "creates a handler function"
    (let [handler (sut/create-handler)]
      (should (fn? handler))))

  (it "handler responds to GET /status"
    (let [handler  (sut/create-handler)
          response (handler {:request-method :get :uri "/status"})]
      (should= 200 (:status response))))

  (it "handler returns 404 for unknown routes"
    (let [handler  (sut/create-handler)
          response (handler {:request-method :get :uri "/nope"})]
      (should= 404 (:status response))))

  (describe "request logging"

    (it "logs request start"
      (let [logged (atom [])
            handler (sut/create-handler)]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (handler {:request-method :get :uri "/status"}))
        (should (some #(= :http/request-start (get-in % [:data :event])) @logged))))

    (it "logs request finish with status and latency"
      (let [logged (atom [])
            handler (sut/create-handler)]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (handler {:request-method :get :uri "/status"}))
        (let [finish (first (filter #(= :http/request-finish (get-in % [:data :event])) @logged))]
          (should-not-be-nil finish)
          (should= 200 (get-in finish [:data :status]))
          (should-not-be-nil (get-in finish [:data :ms])))))

    (it "logs the request method and uri"
      (let [logged (atom [])
            handler (sut/create-handler)]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (handler {:request-method :get :uri "/status"}))
        (let [start (first (filter #(= :http/request-start (get-in % [:data :event])) @logged))]
          (should= :get (get-in start [:data :method]))
          (should= "/status" (get-in start [:data :uri])))))

    (it "logs exceptions with request context"
      (let [logged  (atom [])
            handler (sut/create-handler (fn [_] (throw (Exception. "handler exploded"))))]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (try (handler {:request-method :get :uri "/boom"}) (catch Exception _)))
        (let [err (first (filter #(= :error (:level %)) @logged))]
          (should-not-be-nil err)
          (should= :http/request-error (get-in err [:data :event])))))

    )

  )
