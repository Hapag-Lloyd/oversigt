import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigDashboardsComponent } from './config-dashboards.component';

describe('ConfigDashboardsComponent', () => {
  let component: ConfigDashboardsComponent;
  let fixture: ComponentFixture<ConfigDashboardsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigDashboardsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigDashboardsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
