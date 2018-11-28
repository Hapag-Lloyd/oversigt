import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EventSourceService, ServiceInfo, EventSourceInstanceDetails, EventSourceDescriptor } from 'src/oversigt-client';
import { EventsourceSelectionService } from '../../eventsource-selection.service';
import { Subscribable, Subscription } from 'rxjs';
import { NzMessageService } from 'ng-zorro-antd';
import { ConfigEventsourcesComponent } from '../eventsources/config-eventsources.component';

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
  private subscription: Subscription = null;
  eventSourceId: string = null;
  eventSourceDescriptor: EventSourceDescriptor = null;
  parsedInstanceDetails: ParsedEventSourceInstanceDetails = null;
  serviceInfo: ServiceInfo = null;

  isStartingEventSource = false;
  isStoppingEventSource = false;
  isEnablingEventSource = false;
  isSavingEventSource = false;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private message: NzMessageService,
    private eventSourceSelection: EventsourceSelectionService,
    private ess: EventSourceService,
    private configEventSourcesComponent: ConfigEventsourcesComponent,
  ) { }

  ngOnInit() {
    this.route.url.subscribe(_ => {
      this.initComponent();
    });
    this.initComponent();
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
    console.log('Remove', name);
    console.log(this.parsedInstanceDetails.dataItems[name]);
    this.parsedInstanceDetails.dataItems[name] = undefined;
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
    const props = {};
    Object.keys(parsed.properties).forEach(key =>
      props[key] =  this.eventSourceDescriptor.properties.find(p => p.name === key).inputType === 'json'
                  ? JSON.stringify(parsed.properties[key])
                  : parsed.properties[key]);
    return {  eventSourceDescriptor: parsed.eventSourceDescriptor,
      id: parsed.id,
      name: parsed.name,
      enabled: parsed.enabled,
      frequency: parsed.frequency,
      properties: props,
      dataItems: parsed.dataItems };
  }

  saveConfiguration() {
    this.isSavingEventSource = true;
    console.log(this.parsedInstanceDetails);
    this.ess.updateInstance(this.eventSourceId, this.serializeInstanceDetails(this.parsedInstanceDetails)).subscribe(
      ok => {
        this.message.success('The configuration has been saved.');
      },
      error => {
        console.error(error);
        this.message.error('Saving event source configuration failed. See log for details.');
      },
      () => {
        this.isSavingEventSource = false;
      }
    );
  }

  showUsage() {
    this.message.error('This function is not implemented yet.');
  }

  addToDashboard(id: string) {
    this.message.error('This function is not implemented yet.');
  }

  stopEventSource() {
    this.isStoppingEventSource = true;
    this.ess.setInstanceRunning(this.parsedInstanceDetails.id, false).subscribe(
      ok => {
        this.isStoppingEventSource = false;
        // TODO reload instance and service details
        this.serviceInfo.running = false;
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    this.message.success('The event source "' + this.parsedInstanceDetails.name + '" has been stopped.');
  }

  startEventSource() {
    this.isStartingEventSource = true;
    this.ess.setInstanceRunning(this.parsedInstanceDetails.id, true).subscribe(
      ok => {
        this.isStartingEventSource = false;
        // TODO reload instance and service details
        this.serviceInfo.running = true;
        // TODO nach einiger Zeit nochmal Daten laden, um z.B. Exception-Informationen vom Server zu bekommen
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    this.message.success('The event source "' + this.parsedInstanceDetails.name + '" has been started.');
  }

  disableEventSource() {
    this.changeEnablingState(false,
      () => this.message.success('The event source has been disabled.'),
      () => this.message.error('The event has not been disabled.'));
  }

  enableEventSource() {
    this.changeEnablingState(true,
      () => this.message.success('The event source has been enabled.'),
      () => this.message.error('The event has not been enabled.'));
  }

  private changeEnablingState(enabled: boolean, ok: () => {}, fail: () => {}): void {
    const _this = this;
    this.isEnablingEventSource = true;
    // read current state from server
    this.ess.readInstance(this.parsedInstanceDetails.id).subscribe(
      instanceInfo => {
        // change enabled state and send back to server
        instanceInfo.instanceDetails.enabled = enabled;
        _this.ess.updateInstance(_this.parsedInstanceDetails.id, instanceInfo.instanceDetails).subscribe(
          success => {
            _this.parsedInstanceDetails.enabled = enabled;
            _this.isEnablingEventSource = false;
            ok();
          },
          error => {
            console.error(error);
            alert(error);
            // TODO: Error handling
            _this.isEnablingEventSource = false;
            fail();
          }
        );
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
        _this.isEnablingEventSource = false;
        fail();
      }
    );
  }

  deleteEventSource() {
    // TODO bildschirm blocken
    this.ess.deleteInstance(this.parsedInstanceDetails.id).subscribe(
      ok => {
        // TODO aus der Liste der exisierenden EventSources löschen
        const messageId = this.message.success('Event source "' + this.parsedInstanceDetails.name + '" has been deleted.',
          {nzDuration: 0}).messageId;
        this.configEventSourcesComponent.removeEventSourceInstance(this.parsedInstanceDetails.id);
        setTimeout(() => {
          this.message.remove(messageId);
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
