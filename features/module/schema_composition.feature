Feature: Module schema composition
  At config-load time, every declared module's :extends fragment merges
  into the cfg schema before validation runs. Modules contribute slot
  config keys discriminated by :type. Validation is strict (no
  coercion); invalid values produce validation errors.

  Manifest field schemas use c3kit.apron.schema's vocabulary directly,
  restricted to refs (no inline function literals). Only refs registered
  in isaac core or apron's standard catalog (c3kit.apron.schema.refs) may
  appear under :validations / :coercions — modules may not register their
  own refs, and isaac does NOT load module Clojure code at validation
  time (only the manifest EDN).

  Conditional presence is expressed with the entity-scoped factory ref
  :present-when?, e.g. `[:present-when? :type :telly]`. Enum-style
  validation uses apron's `[:one-of? ...]`. The same shape applies to
  every manifest-extensible surface: :comm, :provider, :tool, and
  :slash-commands.

  Scenario: Module :extends adds slot config keys for its impl
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms   {:bert {:type :telly :loft "rooftop"}}}
      """
    When the config is loaded
    Then the loaded config has:
      | key             | value   |
      | comms.bert.loft | rooftop |

  Scenario: Extended slot fields enforce their schema
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms   {:bert {:type :telly :loft 42}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key             | value            |
      | comms.bert.loft | must be a string |

  Scenario: Without the module declared, extended keys are unknown
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:comms {:bert {:type :fictional :loft "rooftop"}}}
      """
    When the config is loaded
    Then the config has validation warnings matching:
      | key             | value       |
      | comms.bert.loft | unknown key |

  Scenario: Manifest field marked [:present-when? :type X] errors when omitted
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms   {:bert {:type :telly}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key             | value                  |
      | comms.bert.loft | is required when type  |

  Scenario: Manifest [:one-of? ...] rejects values outside the enum
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms   {:bert {:type :telly :loft "rooftop" :mood "elated"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key             | value           |
      | comms.bert.mood | must be one of  |

  Scenario: Manifest [:one-of? ...] accepts values inside the enum
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}
       :comms   {:bert {:type :telly :loft "rooftop" :mood "happy"}}}
      """
    When the config is loaded
    Then the loaded config has:
      | key             | value |
      | comms.bert.mood | happy |

  Scenario: Manifest schema validation applies to provider fields
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules   {:isaac.providers.kombucha {:local/root "modules/isaac.providers.kombucha"}}
       :providers {:tea {:type :kombucha :fizz-level "high"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                       | value                |
      | providers.tea.fizz-level  | must be an integer   |

  Scenario: Manifest schema validation applies to tool fields
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:tools {:web_search {:provider :brave}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                  | value           |
      | tools.web_search.api-key | is required |

  Scenario: Manifest schema validation applies to slash-command fields
    Given an empty Isaac root at "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules        {:isaac.slash.echo {:local/root "modules/isaac.slash.echo"}}
       :slash-commands {:echo {:command-name 42}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                              | value            |
      | slash-commands.echo.command-name | must be a string |

  Scenario: Manifest referencing an unregistered ref fails fast at module activation
    # Phase 8 (isaac-qqgv): comm contributions moved to the
    # :isaac.server/comm berth (key change only; validation is unchanged).
    Given an empty Isaac root at "/tmp/isaac"
    And a module manifest "modules/isaac.comm.broken/deps.edn":
      """
      {:paths ["resources"]
       :isaac/manifest
       {:id                :isaac.comm.broken
        :version           "0.1.0"
        :isaac.server/comm {:broken {:factory isaac.comm.broken/make
                                     :schema  {:thing {:type :string
                                                       :validations [:no-such-ref?]}}}}}}
      """
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.broken {:local/root "modules/isaac.comm.broken"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                          | value                  |
      | modules.isaac.comm.broken    | unregistered ref :no-such-ref? |

  Scenario: Manifest declaring :type in its :schema fails to load
    # Phase 8 (isaac-qqgv): comm contributions moved to the
    # :isaac.server/comm berth (key change only; the :type-as-slot-
    # discriminator rule is unchanged).
    Given an empty Isaac root at "/tmp/isaac"
    And a module manifest at "/tmp/isaac/badmod/deps.edn":
      """
      {:paths ["resources"]
       :isaac/manifest
       {:id                :isaac.comm.badmod
        :version           "0.1.0"
        :isaac.server/comm {:badmod {:factory isaac.comm.null/make
                                     :schema  {:type {:type :string}}}}}}
      """
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.badmod {:local/root "/tmp/isaac/badmod"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                       | value                                        |
      | modules.isaac.comm.badmod | :type is the slot discriminator, not a field |
