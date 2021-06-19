(ns clj-lpass.examples
  (:require [clj-lpass.core :as lpass]))

(comment
  (lpass/ls)

  (lpass/show 123456789)

  (->> (lpass/ls true)
       (filter :aws-credentials/access-key-id)
       first
       )
  
  (->> (lpass/ls)
       (filter lpass/credit-card?)
       (map lpass/show*)
       )

  (->> (lpass/ls)
       (filter lpass/bank-account?)
       )
  )
