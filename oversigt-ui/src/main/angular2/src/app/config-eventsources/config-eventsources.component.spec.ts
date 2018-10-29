import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigEventsourcesComponent } from './config-eventsources.component';

describe('ConfigEventsourcesComponent', () => {
  let component: ConfigEventsourcesComponent;
  let fixture: ComponentFixture<ConfigEventsourcesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigEventsourcesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigEventsourcesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
