import { Component, OnInit } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo, DashboardWidgetService } from 'src/oversigt-client';
import { Subject, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { getLinkForDashboardWidget } from 'src/app/app.component';

@Component({
  selector: 'app-config-dashboard-widget-add',
  templateUrl: './config-dashboards-widget-add.component.html',
  styleUrls: ['./config-dashboards-widget-add.component.css']
})
export class ConfigDashboardWidgetAddComponent implements OnInit {
  private searchTerms = new Subject<string>();
  widgets$: Observable<EventSourceInstanceInfo[]>;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private eventSourceService: EventSourceService,
    private dashboardWidgetService: DashboardWidgetService,
  ) { }

  ngOnInit() {
    this.widgets$ = this.searchTerms.pipe(
      startWith(''), // initial search value
      debounceTime(300), // wait 300ms after each keystroke before considering the term
      distinctUntilChanged(), // ignore new term if same as previous term
      switchMap((term: string) => this.eventSourceService.listInstances(term)), // let the server search
    );
  }

  search(filter: string): void {
    this.searchTerms.next(filter);
  }

  addWidget(id: string): void {
    const dashboardId = this.route.snapshot.parent.params['dashboardId'];
    this.dashboardWidgetService.createWidget(dashboardId, id).subscribe(widgetDetails => {
      this.router.navigateByUrl(getLinkForDashboardWidget(dashboardId, widgetDetails.id));
    }, error => {
      // TODO: error handling
      alert(error);
      console.log(error);
    });
  }
}
