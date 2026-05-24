@wip
Feature: Hail router
  The hail router ticks on the shared scheduler, reads pending
  hails, resolves their :frequency into (crew, session) listeners,
  and writes delivery records to per-session inboxes. After all
  matching listeners have been delivered, the pending file moves
  to hail/delivered/<id>.edn with resolution metadata (which crews
  and sessions actually received it) for forensic visibility.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And default Grover setup

  Scenario: a hail with a frequency band routes to a matching session
    Given the isaac EDN file "config/hail/engineering-intercom.edn" exists with:
      | path      | value             | #comment                            |
      | crew-tags | #{:role/engineer} | only engineers receive on this band |
      | reach     | :one              | pick one listener, not all          |
    And the isaac EDN file "config/crew/bartholomew.edn" exists with:
      | path  | value             | #comment                            |
      | model | grover            |                                     |
      | tags  | #{:role/engineer} | matches the band's crew-tags filter |
    And the following sessions exist:
      | name        | crew        |
      | engine-room | bartholomew |
    And the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                          | #comment                               |
      | id        | hail-1                         |                                        |
      | frequency | {:band "engineering-intercom"} | band reference — looked up in registry |
      | payload   | {:dilithium-leak true}         |                                        |
      | from      | :cli                           |                                        |
    When the hail router ticks
    Then the isaac file "hail/pending/hail-1.edn" does not exist
    And the EDN isaac file "hail/sessions/engine-room/inbox/delivery-1.edn" contains:
      | path      | value                          | #comment                            |
      | hail-id   | hail-1                         | back-reference to the original hail |
      | frequency | {:band "engineering-intercom"} | preserved verbatim                  |
      | payload   | {:dilithium-leak true}         |                                     |
    And the EDN isaac file "hail/delivered/hail-1.edn" contains:
      | path      | value                                                                  | #comment                |
      | id        | hail-1                                                                 | original hail preserved |
      | listeners | [{:crew :bartholomew :session :engine-room :delivery-id "delivery-1"}] | who received it         |

  Scenario: a hail with a direct crew frequency routes to that crew's session
    Given the isaac EDN file "config/crew/hieronymus.edn" exists with:
      | path  | value             |
      | model | grover            |
      | tags  | #{:role/botanist} |
    And the following sessions exist:
      | name       | crew       |
      | greenhouse | hieronymus |
    And the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                   | #comment                                       |
      | id        | hail-1                  |                                                |
      | frequency | {:crew [:hieronymus]}   | direct-crew addressing — no band lookup needed |
      | prompt    | The lettuce is wilting. | required for direct addressing (no band .md)   |
      | payload   | {:wilting [:lettuce]}   |                                                |
      | from      | :cli                    |                                                |
    When the hail router ticks
    Then the isaac file "hail/pending/hail-1.edn" does not exist
    And the EDN isaac file "hail/sessions/greenhouse/inbox/delivery-1.edn" contains:
      | path      | value                   | #comment                                  |
      | hail-id   | hail-1                  |                                           |
      | frequency | {:crew [:hieronymus]}   | original frequency preserved              |
      | prompt    | The lettuce is wilting. | hail's prompt carried forward to delivery |
      | payload   | {:wilting [:lettuce]}   |                                           |
    And the EDN isaac file "hail/delivered/hail-1.edn" contains:
      | path      | value                                                                |
      | listeners | [{:crew :hieronymus :session :greenhouse :delivery-id "delivery-1"}] |

  Scenario: a hail with a direct session frequency routes to that exact session
    Given the isaac EDN file "config/crew/mavis.edn" exists with:
      | path  | value              |
      | model | grover             |
      | tags  | #{:role/navigator} |
    And the following sessions exist:
      | name           | crew  |
      | charted-course | mavis |
      | side-quest     | mavis |
    And the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                        | #comment                                            |
      | id        | hail-1                       |                                                     |
      | frequency | {:session [:charted-course]} | targets ONE specific session, not the crew's others |
      | prompt    | Adjust bearing 12 degrees.   |                                                     |
      | payload   | {:bearing-delta 12}          |                                                     |
      | from      | :cli                         |                                                     |
    When the hail router ticks
    Then the isaac file "hail/pending/hail-1.edn" does not exist
    And the EDN isaac file "hail/sessions/charted-course/inbox/delivery-1.edn" contains:
      | path      | value                        |
      | hail-id   | hail-1                       |
      | frequency | {:session [:charted-course]} |
      | prompt    | Adjust bearing 12 degrees.   |
    And the isaac file "hail/sessions/side-quest/inbox/delivery-1.edn" does not exist
    And the EDN isaac file "hail/delivered/hail-1.edn" contains:
      | path      | value                                                              |
      | listeners | [{:crew :mavis :session :charted-course :delivery-id "delivery-1"}] |

  Scenario: a :reach :one tag-filter picks the idle session over the in-flight one
    Given the isaac EDN file "config/crew/atticus.edn" exists with:
      | path  | value            |
      | model | grover           |
      | tags  | #{:role/command} |
    And the isaac EDN file "config/crew/cordelia.edn" exists with:
      | path  | value            |
      | model | grover           |
      | tags  | #{:role/command} |
    And the following sessions exist:
      | name        | crew     |
      | bridge      | atticus  |
      | first-watch | cordelia |
    And session "first-watch" is in flight
    And the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                         | #comment                            |
      | id        | hail-1                        |                                     |
      | frequency | {:crew-tags #{:role/command}} | any command-tagged crew is eligible |
      | reach     | :one                          | pick exactly one IDLE listener      |
      | prompt    | Status report?                |                                     |
      | from      | :cli                          |                                     |
    When the hail router ticks
    Then the isaac file "hail/pending/hail-1.edn" does not exist
    And the EDN isaac file "hail/sessions/bridge/inbox/delivery-1.edn" contains:
      | path    | value  | #comment                                           |
      | hail-id | hail-1 | atticus's bridge got it (cordelia's was in flight) |
    And the isaac file "hail/sessions/first-watch/inbox/delivery-1.edn" does not exist
    And the EDN isaac file "hail/delivered/hail-1.edn" contains:
      | path      | value                                                          | #comment                      |
      | id        | hail-1                                                         | original hail preserved       |
      | frequency | {:crew-tags #{:role/command}}                                  |                               |
      | listeners | [{:crew :atticus :session :bridge :delivery-id "delivery-1"}] | forensic record of resolution |

  Scenario: a hail with :reach :all delivers to every matching session
    Given the isaac EDN file "config/crew/atticus.edn" exists with:
      | path  | value            |
      | model | grover           |
      | tags  | #{:role/command} |
    And the isaac EDN file "config/crew/cordelia.edn" exists with:
      | path  | value            |
      | model | grover           |
      | tags  | #{:role/command} |
    And the following sessions exist:
      | name        | crew     |
      | bridge      | atticus  |
      | first-watch | cordelia |
    And the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                         | #comment                 |
      | id        | hail-1                        |                          |
      | frequency | {:crew-tags #{:role/command}} |                          |
      | reach     | :all                          | broadcast to every match |
      | prompt    | Red alert!                    |                          |
      | from      | :cli                          |                          |
    When the hail router ticks
    Then the isaac file "hail/pending/hail-1.edn" does not exist
    And the EDN isaac file "hail/sessions/bridge/inbox/delivery-1.edn" contains:
      | path    | value  | #comment       |
      | hail-id | hail-1 | atticus got it |
    And the EDN isaac file "hail/sessions/first-watch/inbox/delivery-1.edn" contains:
      | path    | value  | #comment        |
      | hail-id | hail-1 | cordelia got it |
    And the EDN isaac file "hail/delivered/hail-1.edn" contains:
      | path      | value                                                                                                                            | #comment    |
      | listeners | [{:crew :atticus :session :bridge :delivery-id "delivery-1"} {:crew :cordelia :session :first-watch :delivery-id "delivery-1"}] | both got it |

  Scenario: a hail with an unknown band stays in pending for retry
    Given the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                  | #comment                               |
      | id        | hail-1                 |                                        |
      | frequency | {:band "phantom-band"} | no config/hail/phantom-band.edn exists |
      | payload   | {:n 1}                 |                                        |
      | from      | :cli                   |                                        |
    When the hail router ticks
    Then the EDN isaac file "hail/pending/hail-1.edn" contains:
      | path | value  | #comment              |
      | id   | hail-1 | still pending — retry |
    And the isaac file "hail/delivered/hail-1.edn" does not exist

  Scenario: a hail with no matching listeners stays in pending
    Given the isaac EDN file "config/hail/engineering-intercom.edn" exists with:
      | path      | value             |
      | crew-tags | #{:role/engineer} |
      | reach     | :one              |
    And the isaac EDN file "config/crew/hieronymus.edn" exists with:
      | path  | value             | #comment                       |
      | model | grover            |                                |
      | tags  | #{:role/botanist} | no engineer-tagged crew exists |
    And the following sessions exist:
      | name       | crew       |
      | greenhouse | hieronymus |
    And the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                          | #comment                  |
      | id        | hail-1                         |                           |
      | frequency | {:band "engineering-intercom"} | band exists, no engineers |
      | payload   | {:n 1}                         |                           |
      | from      | :cli                           |                           |
    When the hail router ticks
    Then the EDN isaac file "hail/pending/hail-1.edn" contains:
      | path | value  | #comment                                  |
      | id   | hail-1 | still pending — no engineer to receive it |
    And the isaac file "hail/delivered/hail-1.edn" does not exist

  Scenario: combined band and session-tag form an intersection
    Given the isaac EDN file "config/hail/engineering-intercom.edn" exists with:
      | path      | value             |
      | crew-tags | #{:role/engineer} |
      | reach     | :one              |
    And the isaac EDN file "config/crew/bartholomew.edn" exists with:
      | path  | value             |
      | model | grover            |
      | tags  | #{:role/engineer} |
    And the following sessions exist:
      | name           | crew        | tags                  |
      | engine-room    | bartholomew | #{}                   |
      | coil-tinkering | bartholomew | #{:project/warp-coil} |
    And the EDN isaac file "hail/pending/hail-1.edn" exists with:
      | path      | value                                                              | #comment                                           |
      | id        | hail-1                                                             |                                                    |
      | frequency | {:band "engineering-intercom" :session-tags #{:project/warp-coil}} | band sets the crew filter; hail narrows by session |
      | payload   | {:resonance-drift 0.03}                                            |                                                    |
      | from      | :cli                                                               |                                                    |
    When the hail router ticks
    Then the isaac file "hail/pending/hail-1.edn" does not exist
    And the EDN isaac file "hail/sessions/coil-tinkering/inbox/delivery-1.edn" contains:
      | path    | value  | #comment                                            |
      | hail-id | hail-1 | warp-coil-tagged session got it, engine-room didn't |
    And the isaac file "hail/sessions/engine-room/inbox/delivery-1.edn" does not exist
    And the EDN isaac file "hail/delivered/hail-1.edn" contains:
      | path      | value                                                                     | #comment               |
      | listeners | [{:crew :bartholomew :session :coil-tinkering :delivery-id "delivery-1"}] | only the warp-coil one |
