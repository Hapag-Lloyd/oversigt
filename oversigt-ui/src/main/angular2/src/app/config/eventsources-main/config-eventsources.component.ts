import { Component, OnInit, OnDestroy } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo, DashboardShortInfo, EventSourceInfo } from 'src/oversigt-client';
import { Router, ActivatedRoute, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventsourceSelectionService } from '../../eventsource-selection.service';

@Component({
  selector: 'app-config-eventsources',
  templateUrl: './config-eventsources.component.html',
  styleUrls: ['./config-eventsources.component.css']
})
export class ConfigEventsourcesComponent implements OnInit, OnDestroy {
  selectedEventSource: EventSourceInstanceInfo = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
  ) {
  }

  ngOnInit() {
  }

  ngOnDestroy() {
  }

  getActiveChild(): string {
    const snapshot = this.route.snapshot;
    if (snapshot.children !== undefined && snapshot.children[0] !== undefined) {
      return this.route.snapshot.children[0].url[0].path;
    }
    return '';
  }

  isDetailActive(): boolean {
    const ar = this.getActiveChild();
    return ar !== 'list' && ar !== 'create';
  }

  hasSelectedChild(): boolean {
    return this.route.snapshot.children.length > 0;
  }
}
