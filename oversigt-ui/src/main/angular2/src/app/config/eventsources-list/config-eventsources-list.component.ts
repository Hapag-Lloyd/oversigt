import { Component, OnInit, OnDestroy } from '@angular/core';
import { EventSourceInstanceInfo, EventSourceService } from 'src/oversigt-client';
import { Subject, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';

@Component({
  selector: 'app-config-eventsources-list',
  templateUrl: './config-eventsources-list.component.html',
  styleUrls: ['./config-eventsources-list.component.css']
})
export class ConfigEventsourcesListComponent implements OnInit, OnDestroy {
  private searchTerms = new Subject<string>();
  private lastSearchTerm = '';
  sources$: Observable<EventSourceInstanceInfo[]>;

  constructor(
    private eventSourceService: EventSourceService,
  ) { }

  ngOnInit() {
    this.sources$ = this.searchTerms.pipe(
      startWith(''), // initial search value
      debounceTime(300), // wait 300ms after each keystroke before considering the term
      distinctUntilChanged(), // ignore new term if same as previous term
      switchMap((term: string) => this.eventSourceService.listInstances(term)), // let the server search
    );
    this.scheduleRefresh();
  }

  ngOnDestroy() {
    this.lastSearchTerm = null;
  }

  search(filter: string): void {
    this.lastSearchTerm = filter;
    this.searchTerms.next(filter);
  }

  private scheduleRefresh() {
    setTimeout(() => {
      if (this.lastSearchTerm !== null) {
        this.searchTerms.next(this.lastSearchTerm);
        this.scheduleRefresh();
      }
    }, 60 * 1000);
  }
}
