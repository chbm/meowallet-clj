(ns meowallet-clj.core
  (:use [slingshot.slingshot :only [throw+ try+]]))

(require '[clj-http.client :as client])
(require '[cheshire.core :as json])
(import '(java.io StringReader BufferedReader))


(def base-url "https://services.sandbox.meowallet.pt/api/v2/")
(def test-key "f0bd99cab8c6fadf7ae414e37667d7c2973cff65")

(def sample-checkout 
  {
   :url_confirm "http://"
   :url_cancel "http://"
   :payment {
             :amount 10 :currency "EUR"
             :client {:name "john"}
             :items [ {:name "things" :qt 1} ] 
             }

   })

(defn map-checkout 
  [{:keys [url_confirm url_cancel amount currency client items] :or {currency "EUR"}}]
  {:pre [(some? amount)
         (= currency "EUR")]}
  (merge {:payment (merge {:amount amount :currency currency} 
                          (if (map? client) {:client client})
                          (if (vector? items) {:items items})
                          )}
         (if (some? url_confirm) {:url_confirm url_confirm})
         (if (some? url_cancel) {:url_confirm url_cancel}) )
  )


(defn mw-post [k rsc params]
  (try+
    (let [resp (client/post (str base-url rsc)
               {:headers {:Authorization (str "WalletPT " k)}
                :content-type :json
                :body (json/generate-string params)})]
            (json/parse-string (resp :body)))
    (catch Object e
      (let [] e))
    )
  )

(defn new-checkout 
  "create a new checkout and return the checkout id and continuation url"
  [k params]
  (let [resp (mw-post k "checkout"  (map-checkout params))]
    resp
  )
)
(defn with-key [k]
  (fn [op & body]
    (apply op k body)))

(defn start-checkout
  "Create a new checkout and return the checkout id"
  [k params]
  (mw-post k "checkout" params)
  )

(defn -main
  "temp test"
  []
  (mw-post "f0bd99cab8c6fadf7ae414e37667d7c2973cff65" "checkout" {:amount 10 :cur "EUR"})
)

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
