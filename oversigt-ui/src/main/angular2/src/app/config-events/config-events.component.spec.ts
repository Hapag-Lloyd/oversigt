import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigEventsComponent } from './config-events.component';

describe('ConfigEventsComponent', () => {
  let component: ConfigEventsComponent;
  let fixture: ComponentFixture<ConfigEventsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigEventsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigEventsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
