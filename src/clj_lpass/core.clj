(ns clj-lpass.core
  (:use [clojure.java.shell :only [sh]])
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]))

(defn- meaningful? [[k v]]
  (cond
    (= v "") false
    (and (= k :last-touch) (= v "0")) false
    :else true))

(defn- prune-non-meaningful [secret]
  (reduce (fn [m kv]
            (if (meaningful? kv)
              (conj m kv)
              m))
          {} secret))

(defn- secret-type [{:keys [note] :as secret}]
  (when-let [s (second (and note
                            (re-find #"^NoteType:(.*)" note)))]
    (csk/->kebab-case-keyword s)))

(defn- ->instant [x]
  (if (string? x)
    (->instant (Integer/parseInt x))
    (java.time.Instant/ofEpochSecond x)))

(defn- alter-times [time-keys secret]
  (reduce (fn [secret time-key]
            (if ((set (keys secret)) time-key)
              (update secret time-key ->instant)
              secret))
          secret time-keys))

(defn- split-note [{:keys [note] :as secret}]
  (let [type (name (secret-type secret))
        begin? #(re-find #"^NoteType:" %)
        end? #(or (re-find #"^Notes:" %)
                  (nil? (second (str/split % #"\n" 2))))]
    (loop [text (second (str/split note #"\n" 2))
           obj {:type (csk/->kebab-case-keyword type)}]
      (let [[_ raw-k v] (re-find #"^(.+?):(.*)" text)
            k (if (or (begin? text) (end? text))
                (csk/->kebab-case-keyword raw-k)
                (keyword type (csk/->kebab-case raw-k)))
            next-obj (assoc obj k (if (end? text)
                                    (str/replace text #"^Notes:" "")
                                    v))]
        (if (or (nil? text) (empty? text) (end? text))
          next-obj
          (recur (second (str/split text #"\n" 2)) next-obj))))))

(defn- incorporate-note [{:keys [note] :as secret}]
  (if (re-find #"^NoteType:" note)
    (-> (merge secret (split-note secret))
        (dissoc :note)
        )
    secret))
            
(defn show*
  "Reveal full secret information using the :id value of `partial-secret`."
  [{:keys [id] :as partial-secret}]
  (->> (sh "lpass" "show" "-j" id)
       :out
       json/read-str
       first
       (cske/transform-keys csk/->kebab-case-keyword)
       (alter-times [:last-modified-gmt :last-touch])
       incorporate-note
       prune-non-meaningful
       ))

(defn show
  "Reveal full secret information for a given `id`."
  [id]
  (show* {:id (str id)}))

(defn- parse-ls-line [line]
  (let [[_ raw-name id] (re-find #"^(.*)\ \[id: (\d+)\]$" line)
        [raw-category name] (str/split raw-name #"/")
        categories (str/split raw-category #"\\")
        info {:id id :name name}]
    (when name
      (if (= categories ["(none)"])
        info
        (merge info {:categories categories})))))

(defn ls
  "Return a list of all the secrets for the currently logged in user.

  By default, each item returned will not be fully expanded (i.e. it will have
  a name but will not yet reveal the password or further sub-secrets. To fully
  reveal, either call `show*` or set the `show-all?` argument to a truthy val."
  ([] (ls false))
  ([show-all?]
   (let [rtn (->> (sh "lpass" "ls")
                  :out
                  (#(str/split % #"\n"))
                  (map parse-ls-line)
                  (remove nil?))]
     (if show-all?
       (map #(merge % (show* %)) rtn)
       rtn))))

(defn show-by-name
  "Reveal full secret information of the secret with `name`."
  [name]
  (let [candidates (->> (ls)
                        (filter #(= (:name %) name)))]
    (when (empty? candidates)
      (throw ex-info "Did not find a secret with that name." {:name name}))
    (when (not-empty? (rest candidates))
      (throw ex-info "Multiple secrets found with that name."
             {:name name
              :potential-matches candidates}))
    (first candidates)))

(defn- revealed? [secret]
  ;; assumption: if fullname is populated, then the secret is fully revealed
  (:fullname secret))

(defn- note-type? [secret-type {:keys [type] :as secret}]
  (if (revealed? secret)
    (= type (csk/->kebab-case-keyword secret-type))
    (recur secret-type (show* secret))))

(def address? (partial note-type? "Address"))
(def software-license? (partial note-type? "Software License"))
(def credit-card? (partial note-type? "Credit Card"))
(def bank-account? (partial note-type? "Bank Account"))
(def social-security? (partial note-type? "Social Security"))
