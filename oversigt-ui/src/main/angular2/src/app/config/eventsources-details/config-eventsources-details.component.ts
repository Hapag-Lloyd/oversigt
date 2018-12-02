import { Component, OnInit, OnDestroy, ViewChildren, QueryList } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EventSourceService, ServiceInfo, EventSourceInstanceDetails, EventSourceDescriptor } from 'src/oversigt-client';
import { EventsourceSelectionService } from '../../eventsource-selection.service';
import { Subscribable, Subscription } from 'rxjs';
import { ConfigEventsourcesComponent } from '../eventsources-main/config-eventsources.component';
import { ConfigEventsourceEditorComponent } from '../eventsource-editor/config-eventsource-editor.component';
import { ClrLoadingState } from '@clr/angular';
import { NotificationService } from 'src/app/notification.service';

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
  @ViewChildren(ConfigEventsourceEditorComponent) editors !: QueryList<ConfigEventsourceEditorComponent>;

  private subscription: Subscription = null;
  eventSourceId: string = null;
  eventSourceDescriptor: EventSourceDescriptor = null;
  private _parsedInstanceDetails: ParsedEventSourceInstanceDetails = null;
  serviceInfo: ServiceInfo = null;

  get parsedInstanceDetails(): ParsedEventSourceInstanceDetails {
    return this._parsedInstanceDetails;
  }
  set parsedInstanceDetails(value: ParsedEventSourceInstanceDetails) {
    this._parsedInstanceDetails = value;
  }

  startingEventSourceState = ClrLoadingState.DEFAULT;
  stoppingEventSourceState = ClrLoadingState.DEFAULT;
  enablingEventSourceState = ClrLoadingState.DEFAULT;
  savingEventSourceState = ClrLoadingState.DEFAULT;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private notification: NotificationService,
    private eventSourceSelection: EventsourceSelectionService,
    private ess: EventSourceService,
    private configEventSourcesComponent: ConfigEventsourcesComponent,
  ) { }

  ngOnInit() {
    this.subscription = this.route.url.subscribe(_ => {
      this.initComponent();
    });
  }

  ngOnDestroy() {
    if (this.subscription !== null) {
      this.subscription.unsubscribe();
    }
  }

  private initComponent() {
    // find selected event source id
    this.eventSourceId = this.route.snapshot.paramMap.get('id');
    this.eventSourceSelection.selectEventSource(this.eventSourceId);

    // Reset component
    this.parsedInstanceDetails = null;
    this.eventSourceDescriptor = null;

    // Load data from server
    this.ess.readInstance(this.eventSourceId).subscribe(
      fullInfo => {
        this.serviceInfo = fullInfo.serviceInfo;
        this.ess.getEventSourceDetails(fullInfo.instanceDetails.eventSourceDescriptor).subscribe(
          eventSourceDescriptor => {
            this.eventSourceDescriptor = eventSourceDescriptor;
            this.parsedInstanceDetails = this.parseInstanceDetails(fullInfo.instanceDetails);
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
    this.savingEventSourceState = ClrLoadingState.LOADING;
    console.log(this.parsedInstanceDetails);
    this.ess.updateInstance(this.eventSourceId, this.serializeInstanceDetails(this.parsedInstanceDetails)).subscribe(
      ok => {
        this.savingEventSourceState = ClrLoadingState.SUCCESS;
        this.notification.success('The configuration has been saved.');
      },
      error => {
        this.savingEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        this.notification.error('Saving event source configuration failed. See log for details.');
      },
      () => {
        // TODO: Reload info from server
      }
    );
  }

  showUsage() {
    this.notification.error('This function is not implemented yet.');
  }

  addToDashboard(id: string) {
    this.notification.error('This function is not implemented yet.');
  }

  stopEventSource() {
    this.stoppingEventSourceState = ClrLoadingState.LOADING;
    this.ess.setInstanceRunning(this.parsedInstanceDetails.id, false).subscribe(
      ok => {
        this.stoppingEventSourceState = ClrLoadingState.SUCCESS;
        // TODO reload instance and service details
        this.serviceInfo.running = false;
      },
      error => {
        this.stoppingEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    this.notification.success('The event source "' + this.parsedInstanceDetails.name + '" has been stopped.');
  }

  startEventSource() {
    this.startingEventSourceState = ClrLoadingState.LOADING;
    this.ess.setInstanceRunning(this.parsedInstanceDetails.id, true).subscribe(
      ok => {
        this.startingEventSourceState = ClrLoadingState.SUCCESS;
        // TODO reload instance and service details
        this.serviceInfo.running = true;
        // TODO nach einiger Zeit nochmal Daten laden, um z.B. Exception-Informationen vom Server zu bekommen
      },
      error => {
        this.startingEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    this.notification.success('The event source "' + this.parsedInstanceDetails.name + '" has been started.');
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
    this.enablingEventSourceState = ClrLoadingState.LOADING;
    // read current state from server
    this.ess.readInstance(this.parsedInstanceDetails.id).subscribe(
      instanceInfo => {
        // change enabled state and send back to server
        instanceInfo.instanceDetails.enabled = enabled;
        _this.ess.updateInstance(_this.parsedInstanceDetails.id, instanceInfo.instanceDetails).subscribe(
          success => {
            _this.parsedInstanceDetails.enabled = enabled;
            _this.enablingEventSourceState = ClrLoadingState.SUCCESS;
            ok();
          },
          error => {
            console.error(error);
            alert(error);
            // TODO: Error handling
            _this.enablingEventSourceState = ClrLoadingState.ERROR;
            fail();
          }
        );
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
        _this.enablingEventSourceState = ClrLoadingState.ERROR;
        fail();
      }
    );
  }

  deleteEventSource() {
    // TODO bildschirm blocken
    this.ess.deleteInstance(this.parsedInstanceDetails.id).subscribe(
      ok => {
        // TODO aus der Liste der exisierenden EventSources löschen
        this.notification.success('Event source "' + this.parsedInstanceDetails.name + '" has been deleted.');
        // TODO: whatever
        alert('this.configEventSourcesComponent.removeEventSourceInstance(this.parsedInstanceDetails.id);');
        setTimeout(() => {
          this.router.navigateByUrl('/config/eventSources');
        }, 1500);
      },
      error => {
        console.error(error);
        alert(error);
        // TODO if the event source is being used by other widgets... show this to the user
      }
    );
  }
}
