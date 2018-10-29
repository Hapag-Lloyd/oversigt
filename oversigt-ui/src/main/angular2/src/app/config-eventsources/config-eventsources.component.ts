import { Component, OnInit, OnDestroy } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo } from 'src/oversigt-client';
import { Router, ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-config-eventsources',
  templateUrl: './config-eventsources.component.html',
  styleUrls: ['./config-eventsources.component.css']
})
export class ConfigEventsourcesComponent implements OnInit {
  eventSourceInfos: EventSourceInstanceInfo[] = [];
  selectedEventSource: EventSourceInstanceInfo = null;

  constructor(
    private ess: EventSourceService,
  ) { }

  ngOnInit() {
    const _this = this;
    this.initEventSourceInstanceList();

  }

  private initEventSourceInstanceList() {
    this.ess.listInstances().subscribe(
      infos => {
        this.eventSourceInfos = infos;

        const titles = {};
        const ids = [];
        infos.forEach(info => {
          info.usedBy.forEach(use => {
            titles[use.id] = use.title;
            if (ids.indexOf(use.id) < 0) {
              ids.push(use.id);
            }
          });
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
