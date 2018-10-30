import { Component, OnInit, OnDestroy } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo, DashboardShortInfo } from 'src/oversigt-client';
import { Router, ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventsourceSelectionService } from '../eventsource-selection.service';

@Component({
  selector: 'app-config-eventsources',
  templateUrl: './config-eventsources.component.html',
  styleUrls: ['./config-eventsources.component.css']
})
export class ConfigEventsourcesComponent implements OnInit, OnDestroy {
  eventSourceInfos: EventSourceInstanceInfo[] = [];
  selectedEventSource: EventSourceInstanceInfo = null;
  private eventSourceSelectionSubscription: Subscription = null;

  constructor(
    private eventSourceSelection: EventsourceSelectionService,
    private ess: EventSourceService,
  ) {
    this.eventSourceSelectionSubscription = eventSourceSelection.selectedEventSource.subscribe(
      id => {
        this.selectedEventSource = this.eventSourceInfos.find(info => info.id === id);
        alert(this.selectedEventSource.id);
      }
    );
  }

  ngOnInit() {
    this.initEventSourceInstanceList();
  }

  ngOnDestroy() {
    if (this.eventSourceSelectionSubscription !== null) {
      this.eventSourceSelectionSubscription.unsubscribe();
    }
  }

  private initEventSourceInstanceList() {
    this.ess.listInstances().subscribe(
      infos => {
        this.eventSourceInfos = infos;

        const idToName: {[id: string]: string} = {};
        const dashboardToUses: {[id: string]: DashboardShortInfo[]} = {};
        const unusedIds: string[] = [];
        infos.forEach(info => {
          idToName[info.id] = info.name;
          if (info.usedBy !== null && info.usedBy.length > 0) {
            if (dashboardToUses[info.id] === undefined) {
              dashboardToUses[info.id] = [];
            }
            info.usedBy.forEach(ub => dashboardToUses[info.id].push(ub));
          } else {
            unusedIds.push(info.id);
          }
        });
      },
      error => {
        console.error(error);
        alert(error);
        // TODO
      }
    );
  }

  selectEventSource(event: EventSourceInstanceInfo): void {
    this.selectedEventSource = event;
    // this.router.navigateByUrl('/config/eventSources/' + event.id);
  }

  removeEventSourceInstance(id: string): void {
    this.eventSourceInfos = this.eventSourceInfos.filter(info => info.id !== id);
  }
}
