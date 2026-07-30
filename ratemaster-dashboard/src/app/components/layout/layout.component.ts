import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatCardComponent } from '../stat-card/stat-card.component';
import { TrafficChartComponent } from '../traffic-chart/traffic-chart.component';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, StatCardComponent, TrafficChartComponent],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css'
})
export class LayoutComponent {

}
