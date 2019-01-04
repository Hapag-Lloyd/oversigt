import { Component, OnInit, OnDestroy, ViewChildren, QueryList } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
// tslint:disable-next-line:max-line-length
import { EventSourceService, EventSourceInstanceDetails, EventSourceDescriptor, EventSourceInstanceInfo, EventSourceInstanceState, FullEventSourceInstanceInfo } from 'src/oversigt-client';
import { Subscription, Subject, Observable } from 'rxjs';
import { ConfigEventsourceEditorComponent } from '../eventsource-editor/config-eventsource-editor.component';
import { ClrLoadingState } from '@clr/angular';
import { NotificationService } from 'src/app/notification.service';
import { uniqueItems } from 'src/app/utils/arrays';
import { getLinkForId, getLinkForEventSource } from 'src/app/app.component';
import { startWith, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';

export class ParsedEventSourceInstanceDetails {
  eventSourceDescriptor: string;
  id: string;
  name: string;
  enabled: boolean;
  frequency: string;
  properties: { [key: string]: any; };
  dataItems: { [key: string]: string; };
}

@Component({
  selector: 'app-config-eventsources-details',
  templateUrl: './config-eventsources-details.component.html',
  styleUrls: ['./config-eventsources-details.component.css']
})
export class ConfigEventsourcesDetailsComponent implements OnInit, OnDestroy {
  // TODO: Move focus to search field if drop down has been opened.
  @ViewChildren(ConfigEventsourceEditorComponent) editors !: QueryList<ConfigEventsourceEditorComponent>;

  private searchTerms = new Subject<string>();
  sources$: Observable<EventSourceInstanceInfo[]>;

  private subscription: Subscription = null;
  eventSourceId: string = null;
  eventSourceDescriptor: EventSourceDescriptor = null;
  private _parsedInstanceDetails: ParsedEventSourceInstanceDetails = null;
  instanceState: EventSourceInstanceState = null;

  get parsedInstanceDetails(): ParsedEventSourceInstanceDetails {
    return this._parsedInstanceDetails;
  }
  set parsedInstanceDetails(value: ParsedEventSourceInstanceDetails) {
    this._parsedInstanceDetails = value;
  }

  startStopEventSourceState = ClrLoadingState.DEFAULT;
  enableEventSourceState = ClrLoadingState.DEFAULT;
  saveEventSourceState = ClrLoadingState.DEFAULT;
  deleteEventSourceState = ClrLoadingState.DEFAULT;


  // List of recently configured event sources
  recentlyUsed: ParsedEventSourceInstanceDetails[] = [];

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private notification: NotificationService,
    private eventSourceService: EventSourceService,
  ) { }

  ngOnInit() {
    this.subscription = this.route.url.subscribe(_ => {
      this.initComponent();
    });

    // init filter for drop down
    this.sources$ = this.searchTerms.pipe(
      startWith(''), // initial search value
      debounceTime(300), // wait 300ms after each keystroke before considering the term
      distinctUntilChanged(), // ignore new term if same as previous term
      switchMap((term: string) => this.eventSourceService.listInstances(term)), // let the server search
    );
  }

  ngOnDestroy() {
    if (this.subscription !== null) {
      this.subscription.unsubscribe();
    }
  }

  private initComponent() {
    // find selected event source id
    this.eventSourceId = this.route.snapshot.paramMap.get('id');

    // Reset component
    this.parsedInstanceDetails = null;
    this.eventSourceDescriptor = null;

    // Load data from server
    this.eventSourceService.readInstance(this.eventSourceId).subscribe(
      fullInfo => {
        this.instanceState = fullInfo.instanceState;
        this.eventSourceService.getEventSourceDetails(fullInfo.instanceDetails.eventSourceDescriptor).subscribe(
          eventSourceDescriptor => {
            this.eventSourceDescriptor = eventSourceDescriptor;
            this.parsedInstanceDetails = this.parseInstanceDetails(fullInfo.instanceDetails);

            // recently used sources
            let recentlyUsedJson = localStorage.getItem('eventsources.recentlyUsed');
            if (recentlyUsedJson === null || recentlyUsedJson === undefined || recentlyUsedJson === '') {
              recentlyUsedJson = '[]';
            }
            this.recentlyUsed = JSON.parse(recentlyUsedJson);
            this.recentlyUsed.unshift(this.parsedInstanceDetails);
            this.recentlyUsed = uniqueItems(this.recentlyUsed);
            while (this.recentlyUsed.length > 8) {
              this.recentlyUsed.pop();
              // TODO: Check that saved event sources still exist
            }
            localStorage.setItem('eventsources.recentlyUsed', JSON.stringify(this.recentlyUsed));
          }
        );
      }
    );
  }

  deleteDataItem(name: string): void {
    console.log('Remove', name, this.parsedInstanceDetails.dataItems[name]);
    delete this.parsedInstanceDetails.dataItems[name];
    console.log(this.parsedInstanceDetails.dataItems[name]);
  }

  private parseInstanceDetails(details: EventSourceInstanceDetails): ParsedEventSourceInstanceDetails {
    const props = {};
    Object.keys(details.properties).forEach(key =>
      props[key] =  this.eventSourceDescriptor.properties.find(p => p.name === key).inputType === 'json'
                  ? JSON.parse(details.properties[key])
                  : details.properties[key]);
    return {  eventSourceDescriptor: details.eventSourceDescriptor,
      id: details.id,
      name: details.name,
      enabled: details.enabled,
      frequency: details.frequency,
      properties: props,
      dataItems: details.dataItems };
  }

  private serializeInstanceDetails(parsed: ParsedEventSourceInstanceDetails): EventSourceInstanceDetails {
    console.log('Serialize', parsed.dataItems);
    const props = {};
    Object.keys(parsed.properties).forEach(key =>
      props[key] =  this.eventSourceDescriptor.properties.find(p => p.name === key).inputType === 'json'
                  ? JSON.stringify(parsed.properties[key])
                  : parsed.properties[key]);
    // remove data items that have been disabled in the UI
    this.editors
      .filter(e => e.canBeDisabled) // only data items can be disabled
      .filter(e => !e.enabled) // retain only items that have been disabled in the UI
      .map(e => e.property.name) // transform to the data item name to remove
      .forEach(n => delete parsed.dataItems[n]); // remove items
    // create the object to send to the server
    return {  eventSourceDescriptor: parsed.eventSourceDescriptor,
      id: parsed.id,
      name: parsed.name,
      enabled: parsed.enabled,
      frequency: parsed.frequency,
      properties: props,
      dataItems: parsed.dataItems };
  }

  saveConfiguration() {
    this.saveEventSourceState = ClrLoadingState.LOADING;
    console.log(this.parsedInstanceDetails);
    this.eventSourceService.updateInstance(this.eventSourceId, this.serializeInstanceDetails(this.parsedInstanceDetails)).subscribe(
      ok => {
        this.saveEventSourceState = ClrLoadingState.SUCCESS;
        this.notification.success('The configuration has been saved.');
        // TODO: update event source in list of recently used
      },
      error => {
        this.saveEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        this.notification.error('Saving event source configuration failed. See log for details.');
      },
      () => {
        // TODO: Reload info from server
      }
    );
  }

  showUsage() {
    this.eventSourceService.readInstanceUsage(this.eventSourceId).subscribe(
      dashboards => {
        alert(dashboards);
      }, error => {
        console.error(error);
        this.notification.error('Unable to read usage data of this event source.');
      }
    );
  }

  addToDashboard(id: string) {
    this.notification.error('This function is not implemented yet.');
  }

  stopEventSource() {
    this.startStopEventSourceState = ClrLoadingState.LOADING;
    this.eventSourceService.setInstanceRunning(this.parsedInstanceDetails.id, false).subscribe(
      newInstanceState => {
        this.startStopEventSourceState = ClrLoadingState.SUCCESS;
        this.instanceState = newInstanceState;
        this.notification.success('The event source has been stopped.');
      },
      error => {
        this.startStopEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  startEventSource() {
    this.startStopEventSourceState = ClrLoadingState.LOADING;
    this.eventSourceService.setInstanceRunning(this.parsedInstanceDetails.id, true).subscribe(
      newInstanceState => {
        this.startStopEventSourceState = ClrLoadingState.SUCCESS;
        // TODO reload instance and service details
        this.instanceState = newInstanceState;
        this.notification.success('The event source has been started.');
        // TODO: nach einiger Zeit nochmal Daten laden, um z.B. Exception-Informationen vom Server zu bekommen
      },
      error => {
        this.startStopEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  disableEventSource() {
    this.changeEnablingState(false,
      () => this.notification.success('The event source has been disabled.'),
      () => this.notification.error('The event has not been disabled.'));
  }

  enableEventSource() {
    this.changeEnablingState(true,
      () => this.notification.success('The event source has been enabled.'),
      () => this.notification.error('The event has not been enabled.'));
  }

  private changeEnablingState(enabled: boolean, ok: () => void, fail: () => void): void {
    const _this = this;
    this.enableEventSourceState = ClrLoadingState.LOADING;
    // read current state from server
    this.eventSourceService.readInstance(this.parsedInstanceDetails.id).subscribe(
      instanceInfo => {
        // change enabled state and send back to server
        instanceInfo.instanceDetails.enabled = enabled;
        _this.eventSourceService.updateInstance(_this.parsedInstanceDetails.id, instanceInfo.instanceDetails).subscribe(
          success => {
            _this.parsedInstanceDetails.enabled = enabled;
            _this.enableEventSourceState = ClrLoadingState.SUCCESS;
            ok();
          },
          error => {
            console.error(error);
            alert(error);
            // TODO: Error handling
            _this.enableEventSourceState = ClrLoadingState.ERROR;
            fail();
          }
        );
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
        _this.enableEventSourceState = ClrLoadingState.ERROR;
        fail();
      }
    );
  }

  deleteEventSource() {
    if (!confirm('Do you really want to delete the event source "' + this.parsedInstanceDetails.name + '"?')) {
      return;
    }

    // TODO: bildschirm blocken
    this.deleteEventSourceState = ClrLoadingState.LOADING;
    this.eventSourceService.deleteInstance(this.parsedInstanceDetails.id).subscribe(
      ok => {
        // TODO: aus der Liste der exisierenden EventSources löschen
        // Show user that we had a success
        this.notification.success('Event source "' + this.parsedInstanceDetails.name + '" has been deleted.');
        this.eventSourceDescriptor = null;
        this.deleteEventSourceState = ClrLoadingState.SUCCESS;

        // remove source from list of recently used...
        let recentlyUsedJson = localStorage.getItem('eventsources.recentlyUsed');
        if (recentlyUsedJson === null || recentlyUsedJson === undefined || recentlyUsedJson === '') {
          recentlyUsedJson = '[]';
        }
        this.recentlyUsed = JSON.parse(recentlyUsedJson);
        this.recentlyUsed = this.recentlyUsed.filter(item => {
          return item.id !== this.parsedInstanceDetails.id;
        });
        localStorage.setItem('eventsources.recentlyUsed', JSON.stringify(this.recentlyUsed));

        // navigate to the list of event sources
        setTimeout(() => {
          this.router.navigateByUrl(getLinkForId('eventsources') + '/list');
        }, 1000);
      },
      error => {
        this.deleteEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        alert(error);
        // TODO: if the event source is being used by other widgets... show this to the user
      }
    );
  }

  copyEventSource(): void {
    // 1. Create a new event source of same type
    // 2. Copy settings to newly created event source
    this.eventSourceService.createInstance(this.eventSourceDescriptor.key.key).subscribe(
      eventSource => {
        const parsedCopy = {...this.parsedInstanceDetails};
        parsedCopy.name = 'Copy of ' + parsedCopy.name;
        parsedCopy.enabled = false;
        parsedCopy.id = eventSource.id;
        const copy = this.serializeInstanceDetails(parsedCopy);
        this.eventSourceService.updateInstance(eventSource.id, copy).subscribe(
          ok => {
            this.router.navigateByUrl(getLinkForEventSource(copy.id));
          }, error => {
            this.notification.error('The event source copy could not be configured.');
            console.error(error);
            alert(error);
            // TODO: error handling
          }
        );
      }, error => {
        this.notification.error('The event source could not be created.');
        console.error(error);
        alert(error);
        // TODO: error handling
      }
    );
  }

  searchEventSource(filter: string): void {
    this.searchTerms.next(filter);
  }
}
