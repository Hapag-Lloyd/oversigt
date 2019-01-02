import { Component, OnInit, OnDestroy } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo, DashboardShortInfo, EventSourceInfo } from 'src/oversigt-client';
import { Router, ActivatedRoute, RouterOutlet } from '@angular/router';
import { getLinkForId } from 'src/app/app.component';

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
    private eventSourceService: EventSourceService,
  ) {
  }

  ngOnInit() {
    this.navigateToChild();
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

  private navigateToChild(): void {
    // TODO: also navigate to child if we navigate to this page from a child page
    if (this.router.routerState.snapshot.url !== getLinkForId('eventsources')) {
      return;
    }
    this.eventSourceService.listInstances().subscribe(
      ok => {
        if (ok.length > 0) {
          // We already have some sources
          this.router.navigate(['list'], {relativeTo: this.route, replaceUrl: true});
        } else {
          // There are no sources yet
          this.router.navigate(['create'], {relativeTo: this.route, replaceUrl: true});
        }
      }
    );
  }
}
