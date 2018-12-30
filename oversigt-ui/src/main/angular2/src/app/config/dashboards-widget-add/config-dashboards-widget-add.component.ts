import { Component, OnInit } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo } from 'src/oversigt-client';
import { Subject, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';

@Component({
  selector: 'app-config-dashboard-widget-add',
  templateUrl: './config-dashboards-widget-add.component.html',
  styleUrls: ['./config-dashboards-widget-add.component.css']
})
export class ConfigDashboardWidgetAddComponent implements OnInit {
  private searchTerms = new Subject<string>();
  widgets$: Observable<EventSourceInstanceInfo[]>;

  constructor(
    private eventSourceService: EventSourceService,
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
}
