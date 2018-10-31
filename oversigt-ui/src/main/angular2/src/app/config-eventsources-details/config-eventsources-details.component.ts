import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EventSourceService } from 'src/oversigt-client';
import { EventsourceSelectionService } from '../eventsource-selection.service';
import { Subscribable, Subscription } from 'rxjs';

@Component({
  selector: 'app-config-eventsources-details',
  templateUrl: './config-eventsources-details.component.html',
  styleUrls: ['./config-eventsources-details.component.css']
})
export class ConfigEventsourcesDetailsComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;
  urls: string[] = [];

  constructor(
    private route: ActivatedRoute,
    private ess: EventSourceService,
    private selection: EventsourceSelectionService,
  ) { }

  ngOnInit() {
    this.route.url.subscribe(segs => {
      this.urls = segs.map(seg => seg.path);
      console.log('Details: ', this.urls);
    });
  }

  ngOnDestroy() {
    if (this.subscription !== null) {
      this.subscription.unsubscribe();
    }
  }

}
