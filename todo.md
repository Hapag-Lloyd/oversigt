# TODO

## HLAG specific
- Grant tvgwrp user access to RACF group AEDB2DVS
- DeskAlert-Nachrichten im News-Widget

## General

### Bugs
- better catch Exchange-Exceptions and hide them behind distinguishable one. Then catch them correctly.
- Dashboard-Editoren können die Properties-Seite sehen, wenn sie Daten aktualisieren wollen, bekommen sie aber eine 403-Meldung. Sie sollten also wahrscheinlich am besten die ganze Seite gar nicht sehen.

### New Features
- Let ``https://dashboard.ad.hl.lan/`` link to the wiki of this project (or an about page?) instead of a random dashboard. This way interested developers can start contributing faster.
- Enable CPU usage für Remote Windows machines [StackOverflow](http://stackoverflow.com/questions/828432/psexec-access-denied-errors) and integrate PsExec alternative for running from linux [winexe](https://micksmix.wordpress.com/2012/08/09/winexe-a-psexec-like-client-for-accessing-windows-from-linux/)
- Widget-Configuration within Dashboard in a modal dialog
- When resizing widgets: apply new widget size to content immediately
- Export and Import event source and widget configurations
- EventSource-Configuration Menü übersichtlicher machen. Wenn es zu viele EventSourcen werden, wird das Menü oben zu groß. Idee mit Kategorien: [bootsnipp](http://bootsnipp.com/snippets/76KX2) oder [megamenu](http://bootsnipp.com/snippets/featured/bootstrap-mega-menu)
- other grid-layout thingy [packery](http://packery.metafizzy.co)
- Show more event information on troubleshooting page (event life time etc)
- ConfigurationItemEditors: If the possible values are "true" and "false" make a checkbox...
- Die Quelle der EventSources konfigurierbar machen --> Standard ist die integrierte SQLite Datenbank, aber wenn das Dashboard in andere Produkte integriert würde, könnten hier über Guice eigene Quellen eingehängt werden.
- [beautiful spinners](https://bootsnipp.com/snippets/featured/input-spinner-with-min-and-max-values) or [these ones](https://bootsnipp.com/snippets/featured/bootstrap-number-spinner-on-click-hold)
- better [chart widgets](https://bootsnipp.com/snippets/featured/responsive-column-chart)
- maybe some even better and more beautiful check boxes
- Notification widget, that only displays data if something happens... otherwise news... [alert box](https://bootsnipp.com/snippets/featured/alert-messages-like-the-docs
- if the event source has some own timer: let it replace the built-in timer (InternetDownloadEventSource)
- add OversigtProperties to JSON-Editor

### New UI
- [Angular 2 login](http://jasonwatmore.com/post/2016/09/29/angular-2-user-registration-and-login-example-tutorial)

## Done
- Resizable widgets
- Ruthe-Comic
- EDI-Status aus dem HIP
- EventSource nicht neu starten, wenn es nicht sein muss -> nochmal verbessern
- all dashboard will be reloaded at midnight (server time)
- Start up application without event source start. Then you can edit configuration (e.g. passwords).
- Restart EventSource from config-page
- Widgets nur deaktivieren, so dass sie noch im Dashboard sind, aber nicht angezeigt werden
- Reloading-Möglichkeit für einzelne Dashboards schaffen
- Credentials in eigene Datentypen auslagern
- Available Widgets in der Dashboard-Config eventuell mit Screenshot anzeigen: [bootsnipp](http://bootsnipp.com/snippets/1K0md)
- Allow typing select boxes (e.g. for time formats) [idea](http://bootsnipp.com/snippets/featured/advanced-dropdown-search) or [idea](https://silviomoreto.github.io/bootstrap-select/examples/) or [idea](https://www.npmjs.com/package/bootstrap-combobox)
- Override color of widgets from dashboard configuration
- Period & Duration-Editor hübscher machen: [passender Editor](https://jsfiddle.net/0odpuwv9/)
- more beautiful check boxes [example](https://bootsnipp.com/snippets/featured/badgebox-css-checkbox-badge)
- Create better default names for newly created event sources
- Wenn Credentials (oder andere SerializableProperties) geändert werden, alle EventSources neu starten, die diesen Wert nutzen
- Action log to know for auditing
- Authentication
- Jeder EventSource noch eine "Beschreibung" geben, die beinhaltet, was diese EventSource macht. In der Dashboard-Config wird dann beim Erstellen eines Widgets angezeigt, wofür die EventSource gedacht ist
- maybe other [select box](https://select2.github.io/examples.html) implementation - and if too many items: show a search input
- Make typing select box to be used ONLY if you want it. 
- Make typing select boxes to look "correct" on both configuration views. Currently they look a little bit different in widget configuration or in event source configuration
- Possibility for non-admins to create new dashboards or maybe to ask for a new dashboard...
- extract configuration editors into freeloader-templates
- Avoid sending events to "wrong" dashboards
- High-Level-Log, in dem eingetragen wird, wenn z.B. User-Interaktion benötigt wird, z.B. wenn der JIRA-Login mal wieder nicht geht.
