import { Component, OnInit, OnDestroy } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo, DashboardShortInfo, EventSourceInfo } from 'src/oversigt-client';
import { Router, ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventsourceSelectionService } from '../../eventsource-selection.service';

@Component({
  selector: 'app-config-eventsources',
  templateUrl: './config-eventsources.component.html',
  styleUrls: ['./config-eventsources.component.css']
})
export class ConfigEventsourcesComponent implements OnInit, OnDestroy {
  eventSourceInfos: EventSourceInstanceInfo[] = [];
  selectedEventSource: EventSourceInstanceInfo = null;
  private subscriptions: Subscription[] = [];
  private selectedEventSourceIdToBeSelected: string = null;

  constructor(
    private eventSourceSelection: EventsourceSelectionService,
    private route: ActivatedRoute,
    private router: Router,
    private ess: EventSourceService,
  ) {
    const _this_ = this;
    this.subscriptions.push(eventSourceSelection.selectedEventSource.subscribe(id => {
      this.selectedEventSource = this.getEventSource(id);
    }));
    this.subscriptions.push(route.url.subscribe(segs => {
      if (route !== null
          && route.snapshot !== null
          && route.snapshot.firstChild !== null
          && route.snapshot.firstChild.params !== null) {
        _this_.selectedEventSource = _this_.getEventSource(route.snapshot.firstChild.params['id']);
        _this_.selectedEventSourceIdToBeSelected = null;
        if (_this_.selectedEventSource === undefined) {
          _this_.selectedEventSourceIdToBeSelected = route.snapshot.firstChild.params['id'];
        }
      } else {
        _this_.selectedEventSource = null;
      }
    }));

    // TODO alle 5 minuten die Liste der EventSources aktualisieren
  }

  ngOnInit() {
    this.initEventSourceInstanceList();
  }

  ngOnDestroy() {
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  hasSelectedChild(): boolean {
    return this.route.snapshot.children.length > 0;
  }

  private initEventSourceInstanceList() {
    this.ess.listInstances().subscribe(
      infos => {
        this.eventSourceInfos = infos;
        if (this.selectedEventSourceIdToBeSelected !== null) {
          this.selectedEventSource = this.getEventSource(this.selectedEventSourceIdToBeSelected);
          this.selectedEventSourceIdToBeSelected = null;
        }
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  private getEventSource(id: string): EventSourceInstanceInfo {
    return this.eventSourceInfos.find(info => info.id === id);
  }

  selectEventSource(id: string | EventSourceInstanceInfo): void {
    this.router.navigateByUrl('/config/eventSources/' + (typeof id === 'string' ? id : id.id));
  }

  removeEventSourceInstance(id: string): void {
    this.eventSourceInfos = this.eventSourceInfos.filter(info => info.id !== id);
  }
}
