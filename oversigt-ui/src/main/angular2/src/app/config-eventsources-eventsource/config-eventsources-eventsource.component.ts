import { Component, OnInit, OnDestroy, Output, EventEmitter } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventSourceService, EventSourceInstanceDetails, EventSourceDescriptor, ServiceInfo, Dashboard } from 'src/oversigt-client';
import { NzNotificationService, NzMessageService } from 'ng-zorro-antd';
import { ConfigEventsourcesComponent } from '../config-eventsources/config-eventsources.component';
import { EventsourceSelectionService } from '../eventsource-selection.service';

@Component({
  selector: 'app-config-eventsources-eventsource',
  templateUrl: './config-eventsources-eventsource.component.html',
  styleUrls: ['./config-eventsources-eventsource.component.css']
})
export class ConfigEventsourcesEventsourceComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;
  eventSourceId: string = null;
  eventSourceDescriptor: EventSourceDescriptor = null;
  instanceDetails: EventSourceInstanceDetails = null;
  serviceInfo: ServiceInfo = null;

  isStartingEventSource = false;
  isStoppingEventSource = false;
  isEnablingEventSource = false;

  constructor(
    private eventSourceSelection: EventsourceSelectionService,
    private route: ActivatedRoute,
    private router: Router,
    private ess: EventSourceService,
    private message: NzMessageService,
    private configEventSourcesComponent: ConfigEventsourcesComponent,
  ) { }

  ngOnInit() {
    this.subscription = this.route.params.subscribe(params => {
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
    this.instanceDetails = null;
    this.eventSourceDescriptor = null;

    // Load data from server
    this.ess.readInstance(this.eventSourceId).subscribe(
      fullInfo => {
        this.instanceDetails = fullInfo.instanceDetails;
        this.serviceInfo = fullInfo.serviceInfo;
        this.ess.getEventSourceDetails(this.instanceDetails.eventSourceDescriptor).subscribe(
          eventSourceDescriptor => this.eventSourceDescriptor = eventSourceDescriptor
        );
      }
    );
  }

  saveConfiguration() {
    this.ess.updateInstance(this.eventSourceId, this.instanceDetails).subscribe(
      ok => {
        this.message.success('The configuration has been saved.');
      },
      error => {
        console.error(error);
        this.message.error('Saving event source configuration failed. See log for details.');
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
    this.ess.setInstanceRunning(this.instanceDetails.id, false).subscribe(
      ok => {
        this.isStoppingEventSource = false;
        // TODO reload instance and service details
        this.serviceInfo.running = false;
      },
      error => {
        console.error(error);
        alert(error);
        // TODO
      }
    );
    this.message.success('The event source "' + this.instanceDetails.name + '" has been stopped.');
  }

  startEventSource() {
    this.isStartingEventSource = true;
    this.ess.setInstanceRunning(this.instanceDetails.id, true).subscribe(
      ok => {
        this.isStartingEventSource = false;
        // TODO reload instance and service details
        this.serviceInfo.running = true;
        // TODO nach einiger Zeit nochmal Daten laden, um z.B. Exception-Informationen vom Server zu bekommen
      },
      error => {
        console.error(error);
        alert(error);
        // TODO
      }
    );
    this.message.success('The event source "' + this.instanceDetails.name + '" has been started.');
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
    this.ess.readInstance(this.instanceDetails.id).subscribe(
      instanceInfo => {
        // change enabled state and send back to server
        instanceInfo.instanceDetails.enabled = enabled;
        _this.ess.updateInstance(_this.instanceDetails.id, instanceInfo.instanceDetails).subscribe(
          success => {
            _this.instanceDetails.enabled = enabled;
            _this.isEnablingEventSource = false;
            ok();
          },
          error => {
            console.error(error);
            alert(error);
            // TODO
            _this.isEnablingEventSource = false;
            fail();
          }
        );
      },
      error => {
        console.error(error);
        alert(error);
        // TODO
        _this.isEnablingEventSource = false;
        fail();
      }
    );
  }

  deleteEventSource() {
    // TODO bildschirm blocken
    this.ess.deleteInstance(this.instanceDetails.id).subscribe(
      ok => {
        // TODO aus der Liste der exisierenden EventSources löschen
        const messageId = this.message.success('Event source "' + this.instanceDetails.name + '" has been deleted.',
          {nzDuration: 0}).messageId;
        this.configEventSourcesComponent.removeEventSourceInstance(this.instanceDetails.id);
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
