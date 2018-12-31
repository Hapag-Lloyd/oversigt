import { Component, OnInit, OnDestroy, ViewChildren, QueryList } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EventSourceService, ServiceInfo, EventSourceInstanceDetails, EventSourceDescriptor } from 'src/oversigt-client';
import { EventsourceSelectionService } from '../../eventsource-selection.service';
import { Subscribable, Subscription } from 'rxjs';
import { ConfigEventsourcesComponent } from '../eventsources-main/config-eventsources.component';
import { ConfigEventsourceEditorComponent } from '../eventsource-editor/config-eventsource-editor.component';
import { ClrLoadingState } from '@clr/angular';
import { NotificationService } from 'src/app/notification.service';
import { uniqueItems } from 'src/app/utils/arrays';
import { getLinkForId } from 'src/app/app.component';

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

  startEventSourceState = ClrLoadingState.DEFAULT;
  stopEventSourceState = ClrLoadingState.DEFAULT;
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
        this.serviceInfo = fullInfo.serviceInfo;
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
    this.notification.error('This function is not implemented yet.');
  }

  addToDashboard(id: string) {
    this.notification.error('This function is not implemented yet.');
  }

  stopEventSource() {
    this.stopEventSourceState = ClrLoadingState.LOADING;
    this.eventSourceService.setInstanceRunning(this.parsedInstanceDetails.id, false).subscribe(
      ok => {
        this.stopEventSourceState = ClrLoadingState.SUCCESS;
        // TODO reload instance and service details
        this.serviceInfo.running = false;
      },
      error => {
        this.stopEventSourceState = ClrLoadingState.ERROR;
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    this.notification.success('The event source "' + this.parsedInstanceDetails.name + '" has been stopped.');
  }

  startEventSource() {
    this.startEventSourceState = ClrLoadingState.LOADING;
    this.eventSourceService.setInstanceRunning(this.parsedInstanceDetails.id, true).subscribe(
      ok => {
        this.startEventSourceState = ClrLoadingState.SUCCESS;
        // TODO reload instance and service details
        this.serviceInfo.running = true;
        // TODO nach einiger Zeit nochmal Daten laden, um z.B. Exception-Informationen vom Server zu bekommen
      },
      error => {
        this.startEventSourceState = ClrLoadingState.ERROR;
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
}
