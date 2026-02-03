(ns mdm.isaac.user.create
  "CLI tool for creating users in the database."
  (:require [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [clojure.string :as str]
            [mdm.isaac.user.core :as user]))

(def min-password-length 8)

(defn valid-email? [email]
  (boolean
    (and (string? email)
         (seq email)
         (schema/email? email))))

(defn valid-password? [password]
  (and (string? password)
       (>= (count password) min-password-length)))

(defn- email-exists? [email]
  (some? (db/ffind-by :user :email email)))

(defn create-user!
  "Creates a user with the given email and password.
   Returns {:status :ok :user <user>} on success,
   or {:status :error :message <msg>} on failure."
  [email password]
  (let [email (str/lower-case (or email ""))]
    (cond
      (not (valid-email? email))
      {:status :error :message "Invalid email format"}

      (not (valid-password? password))
      {:status :error :message (str "Password must be at least " min-password-length " characters")}

      (email-exists? email)
      {:status :error :message "Email already in use"}

      :else
      (let [user (db/tx {:kind     :user
                         :email    email
                         :password (user/hash-password password)})]
        {:status :ok :user user}))))

(defn- prompt [msg]
  (print msg)
  (flush)
  (read-line))

(defn- prompt-password [msg]
  (print msg)
  (flush)
  ;; Try to use console for hidden input, fall back to read-line
  (if-let [console (System/console)]
    (String. (.readPassword console))
    (read-line)))

(defn -main
  "Main entry point for creating users."
  [& _args]
  (println "Create User")
  (println "===========")
  (let [email    (prompt "Email: ")
        password (prompt-password "Password: ")
        result   (create-user! email password)]
    (if (= :ok (:status result))
      (println "User created successfully!")
      (println "Error:" (:message result)))))
