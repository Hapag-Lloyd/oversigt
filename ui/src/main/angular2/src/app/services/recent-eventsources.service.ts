import { Injectable } from '@angular/core';
import { ParsedEventSourceInstanceDetails } from '../config/eventsources-details/config-eventsources-details.component';
import { EventSourceService } from 'src/oversigt-client';

const STORAGE_KEY = 'eventsources.recentlyUsed';
const MAX_ITEMS = 15;

function onlyUnique(value, index, self) {
  return self.indexOf(value) === index;
}

function uniqueItems<T>(array: ParsedEventSourceInstanceDetails[]): ParsedEventSourceInstanceDetails[] {
  const ids = array.map(p => p.id);
  const uniqueIds = ids.filter(onlyUnique);
  return uniqueIds.map(id => array.find(p => p.id === id));
}


@Injectable({
  providedIn: 'root'
})
export class RecentEventsourcesService {
  private _recentlyUsed: ParsedEventSourceInstanceDetails[] = [];

  constructor(
    private eventsourceService: EventSourceService,
  ) {
    this.loadRecentlyUsed();
    this.removeNonExistantEventSources();
  }

  private loadRecentlyUsed(): void {
    let recentlyUsedJson = localStorage.getItem(STORAGE_KEY);
    if (recentlyUsedJson === null || recentlyUsedJson === undefined || recentlyUsedJson === '') {
      recentlyUsedJson = '[]';
    }
    this._recentlyUsed = JSON.parse(recentlyUsedJson);
  }

  private removeNonExistantEventSources(): void {
    this.eventsourceService.listInstances().subscribe(list => {
      const ids = list.map(inst => inst.id);
      this._recentlyUsed = this._recentlyUsed.filter(es => ids.indexOf(es.id) !== -1);
      this.saveRecentlyUsed();
    });
  }

  private saveRecentlyUsed(): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(this._recentlyUsed));
  }

  getRecentlyUsed(max: number): ParsedEventSourceInstanceDetails[] {
    return this._recentlyUsed.slice(0, max);
  }

  addEventSource(eventSource: ParsedEventSourceInstanceDetails): void {
    this._recentlyUsed.unshift(eventSource);
    this._recentlyUsed = uniqueItems(this._recentlyUsed);
    while (this._recentlyUsed.length > MAX_ITEMS) {
      this._recentlyUsed.pop();
    }
    this.saveRecentlyUsed();
    this.removeNonExistantEventSources();
  }

  removeEventSource(id: string): void {
    this._recentlyUsed = this._recentlyUsed.filter(es => es.id !== id);
    this.saveRecentlyUsed();
  }
}
