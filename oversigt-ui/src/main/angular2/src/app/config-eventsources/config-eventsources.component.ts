import { Component, OnInit } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo } from 'src/oversigt-client';
import { Router, ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-config-eventsources',
  templateUrl: './config-eventsources.component.html',
  styleUrls: ['./config-eventsources.component.css']
})
export class ConfigEventsourcesComponent implements OnInit {
  eventSourceInfos: EventSourceInstanceInfo[] = [];
  selectedEventSource: EventSourceInstanceInfo = null;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private ess: EventSourceService,
  ) { }

  ngOnInit() {
    const _this = this;
    this.initEventSourceInstanceList();
    this.route.url.subscribe(segments => {
      const paths = _this.route.snapshot.url.map(segment => segment.path);
      console.log('New URL', paths);
      if (paths[0] === 'config' && paths[1] === 'eventSources') {
        if (paths.length > 2) {
          const selectedId = paths[2];
          console.log('Selected:', selectedId);
          _this.selectedEventSource = _this.eventSourceInfos.find(info => info.id === selectedId);
        }
      }
    });
  }

  private initEventSourceInstanceList() {
    this.ess.listInstances().subscribe(
      infos => {
        this.eventSourceInfos = infos;
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
    this.router.navigateByUrl('/config/eventSources/' + event.id);
  }

  removeEventSourceInstance(id: string): void {
    this.eventSourceInfos = this.eventSourceInfos.filter(info => info.id !== id);
  }
}
