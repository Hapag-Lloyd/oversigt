import { Component, OnInit, OnDestroy, ViewChildren, QueryList } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
// tslint:disable-next-line:max-line-length
import { EventSourceService, EventSourceInstanceDetails, EventSourceDescriptor, EventSourceInstanceInfo, EventSourceInstanceState, DashboardInfo, DashboardService } from 'src/oversigt-client';
import { Subscription, Subject, Observable } from 'rxjs';
import { ConfigEventsourceEditorComponent } from '../eventsource-editor/config-eventsource-editor.component';
import { ClrLoadingState } from '@clr/angular';
import { NotificationService } from 'src/app/services/notification.service';
import { getLinkForId, getLinkForEventSource } from 'src/app/app.component';
import { startWith, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { RecentEventsourcesService } from 'src/app/services/recent-eventsources.service';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

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
  // listen for URL changes
  private subscription: Subscription = null;

  // query sub components when saving the event source data
  @ViewChildren(ConfigEventsourceEditorComponent) editors !: QueryList<ConfigEventsourceEditorComponent>;

  // search field in the drop down button
  private searchTerms = new Subject<string>();
  sources$: Observable<EventSourceInstanceInfo[]>;

  // main event source info
  eventSourceId: string = null;
  eventSourceDescriptor: EventSourceDescriptor = null;
  private _parsedInstanceDetails: ParsedEventSourceInstanceDetails = null;
  instanceState: EventSourceInstanceState = null;
  usage: DashboardInfo[] = [];
  addable: DashboardInfo[] = [];

  // parsed instance details for display
  get parsedInstanceDetails(): ParsedEventSourceInstanceDetails {
    return this._parsedInstanceDetails;
  }
  set parsedInstanceDetails(value: ParsedEventSourceInstanceDetails) {
    this._parsedInstanceDetails = value;
  }

  get recentlyUsed(): ParsedEventSourceInstanceDetails[] {
    return this.recentEventSources.getRecentlyUsed(8);
  }

  // loading states
  startStopEventSourceState = ClrLoadingState.DEFAULT;
  enableEventSourceState = ClrLoadingState.DEFAULT;
  saveEventSourceState = ClrLoadingState.DEFAULT;
  deleteEventSourceState = ClrLoadingState.DEFAULT;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private notification: NotificationService,
    private eventSourceService: EventSourceService,
    private dashboardService: DashboardService,
    private recentEventSources: RecentEventsourcesService,
    private errorHandler: ErrorHandlerService,
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
      switchMap((term: string) => this.eventSourceService.listInstances(term, 6)), // let the server search
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
        // read details
        this.eventSourceService.getEventSourceDetails(fullInfo.instanceDetails.eventSourceDescriptor).subscribe(
          eventSourceDescriptor => {
            this.eventSourceDescriptor = eventSourceDescriptor;
            this.parsedInstanceDetails = this.parseInstanceDetails(fullInfo.instanceDetails);

            this.recentEventSources.addEventSource(this.parsedInstanceDetails);
          }
        );

        // read usage
        this.dashboardService.listDashboardIds().subscribe(
          dashboardList => {
            this.eventSourceService.readInstanceUsage(this.eventSourceId).subscribe(
              usageList => {
                this.usage = usageList;
                const usageIds = usageList.map(d => d.id);
                this.addable = dashboardList.filter(d => usageIds.indexOf(d.id) === -1);
              },
              this.errorHandler.createErrorHandler('Reading event source instance')
            );
          },
          this.errorHandler.createErrorHandler('Listing dashboards')
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
        this.recentEventSources.addEventSource(this.parsedInstanceDetails);
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
      this.errorHandler.createErrorHandler('Stopping the event source', () => {
        this.startStopEventSourceState = ClrLoadingState.ERROR;
      })
    );
  }

  startEventSource() {
    this.startStopEventSourceState = ClrLoadingState.LOADING;
    this.eventSourceService.setInstanceRunning(this.parsedInstanceDetails.id, true).subscribe(
      newInstanceState => {
        this.startStopEventSourceState = ClrLoadingState.SUCCESS;
        this.instanceState = newInstanceState;
        this.notification.success('The event source has been started.');
        setTimeout(() => {
          this.loadEventSourceState();
        }, 1000);
        setTimeout(() => {
          this.loadEventSourceState();
        }, 5000);
        setTimeout(() => {
          this.loadEventSourceState();
        }, 20000);
      },
      this.errorHandler.createErrorHandler('Starting the event source', () => {
        this.startStopEventSourceState = ClrLoadingState.ERROR;
      })
    );
  }

  private loadEventSourceState(): void {
    this.eventSourceService.isInstanceRunning(this.parsedInstanceDetails.id).subscribe(
      state => {
        this.instanceState = state;
      },
      this.errorHandler.createErrorHandler('Reading instance information')
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
    // const _this = this;
    this.enableEventSourceState = ClrLoadingState.LOADING;
    // read current state from server
    this.eventSourceService.readInstance(this.parsedInstanceDetails.id).subscribe(
      instanceInfo => {
        // change enabled state and send back to server
        instanceInfo.instanceDetails.enabled = enabled;
        this.eventSourceService.updateInstance(this.parsedInstanceDetails.id, instanceInfo.instanceDetails).subscribe(
          success => {
            this.parsedInstanceDetails.enabled = enabled;
            this.enableEventSourceState = ClrLoadingState.SUCCESS;
            this.loadEventSourceState();
            ok();
          },
          this.errorHandler.createErrorHandler('Changing event source state', () => {
            this.enableEventSourceState = ClrLoadingState.ERROR;
            fail();
          })
        );
      },
      this.errorHandler.createErrorHandler('Reading event source information', () => {
        this.enableEventSourceState = ClrLoadingState.ERROR;
        fail();
      })
    );
  }

  deleteEventSource() {
    if (!confirm('Do you really want to delete the event source "' + this.parsedInstanceDetails.name + '"?')) {
      return;
    }

    this.deleteEventSourceState = ClrLoadingState.LOADING;
    this.eventSourceService.deleteInstance(this.parsedInstanceDetails.id).subscribe(
      ok => {
        this.recentEventSources.removeEventSource(this.parsedInstanceDetails.id);
        // Show user that we had a success
        this.notification.success('Event source "' + this.parsedInstanceDetails.name + '" has been deleted.');
        this.eventSourceDescriptor = null;
        this.deleteEventSourceState = ClrLoadingState.SUCCESS;

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
          },
          this.errorHandler.createErrorHandler('Configuring the event source copy')
        );
      },
      this.errorHandler.createErrorHandler('Creating event source copy')
    );
  }

  searchEventSource(filter: string): void {
    this.searchTerms.next(filter);
  }
}
