(ns meowallet-clj.core
  (:use [slingshot.slingshot :only [throw+ try+]]))

(require '[clj-http.client :as client])
(require '[cheshire.core :as json])
(require '[ring.util.request :as ring-req])

(defmacro def- 
  [symbol & body] 
  `(def ^:private ~symbol ~@body))



(defn with-key-on [env key]
  (let [baseurl (if (= env :production)
                  "https://services.wallet.pt/api/v2/" 
                  "https://services.sandbox.meowallet.pt/api/v2/")]
      (fn [f & body] 
        (apply f baseurl key body))
    ))

(defn- map-checkout 
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

(defn- mw-parse-response 
  [resp]
  (try+
    (json/parse-string (resp :body))
    (catch Object e
      nil)
    )
  )

(defn- mw-call
  [method base-url k rsc body query]
  (try+
    (let [encodedbody (if (map? body)
              (json/generate-string body)
              body
              )]
      (client/request { ;;;:debug true
                       :method method 
                       :url (str base-url rsc)
                       :headers {"authorization" (str "WalletPT " k)}
                       :content-type :json
                       :body encodedbody 
                       :query-params query}))
    (catch Object e
      ;;; TODO something more useful here
      (println (str "mw call exception " e))
;;;      (throw e)
      ))
    )

(defn- mw-post-
  [base-url k rsc body]
  (mw-call :post base-url k rsc body {}))

(defn- mw-post
  [base-url k rsc body]
  (let [resp (mw-call :post base-url k rsc body {})]
    (mw-parse-response resp)
    )
  )

(defn- mw-get
  [base-url k rsc params]
  (let [resp (mw-call :get base-url k rsc "" params)]
    (mw-parse-response resp)))


(defn start-checkout 
  "create a new checkout and return the checkout id and continuation url"
  [base-url k params]
  (let [resp (->> params
                (map-checkout)
                (mw-post base-url k "checkout"))]
    resp
  )
)


(defn- callback-valid? [base-url k thebody]
  (let [resp (mw-post- base-url k "callback/verify" thebody)]
    (if (map? resp)
      (= (resp :status) 200)
      nil
    )
  ))

(def- bad-req-response
  {:status 400
   :header {:content-type "text/plain"}
   :body "bad request"}
  )

(def- good-req-response
  {:status 200
   :header {:content-type "text/plain"}
   :body "OK"}
  )

(defn register-callback
  [base-url k cb]
  (fn [{:keys [headers body request-method] :as request}]
    (let [bodystr (ring-req/body-string request)]
       (if-not (and (= request-method :post) (= (headers "content-type") "application/json"))
   {:status 400
   :header {:content-type "text/plain"}
   :body "my bad request"}
         (if (callback-valid? base-url k bodystr)
           (if (some? (cb (json/parse-string bodystr)))
             good-req-response
             bad-req-response
           ))))))

(defn get-operation
  "get operation by id"
  [base-url k id]
  {:pre [(string? id)]}
  (mw-get base-url k (str "operations/" id) nil))

(defn get-operations
  "get a list of operations for this wallet"
  ([base-url k] (mw-get base-url k "operations" {}))
  ([base-url k query] (mw-get base-url k "operations" query))
  )

(defn get-operations-seq-inner
  [base-url k page]
  (let [b (chunk-buffer 10)
        res (get-operations base-url k {"offset" (* page 10) "limit" 10})]
    (if-not (and (map? res) (contains? res "elements") (not (empty? (res "elements"))))
      nil
      (loop [r (res "elements")]
        (if (empty? r)
          (chunk b)
          (do (chunk-append b (first r))
              (recur (rest r))))))))

(defn get-operations-seq
  "returns a sequence of operations for this wallet"
  ([base-url k] (get-operations-seq base-url k 0))
  ([base-url k page] 
   (lazy-seq 
     (let [c (get-operations-seq-inner base-url k page)]
      (chunk-cons c (when (some? c) (get-operations-seq base-url k (inc page))))))))

(defn get-operations-invoice
  "get operations for an invoiceid"
  [base-url k invoiceid]
  {:pre [(string? invoiceid)]}
  (mw-get base-url k (str "operations/byinvoice/" invoiceid)))

(defn refund
  "refund some amount of an operation"
  [base-url k operation amount extparams]
  {:pre [(map? operation)
         (contains? operation "id")
         (contains? operation "amount")
         (contains? operation "refundable")]}
  (if-not (operation "refundable")
    nil
    (if (> amount (operation "amount"))
      nil
      (mw-post base-url k (str "operations/" (operation "id") "/refund") 
               (merge (if (= amount (operation "amount"))
                        {"type" "full"}
                        {"type" "partial"
                         "amount" amount}) 
                      extparams))
      ))
  )
